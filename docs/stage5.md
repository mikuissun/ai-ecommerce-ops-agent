# Stage 5：多轮 Conversation Memory

## 会话与存储

新增 Flyway V4__create_conversations.sql，之前的迁移不变。
后端启动时由 Flyway 在 MySQL 中创建：

| 表 | 字段 |
|---|---|
| conversations | id、user_id、title、created_at、updated_at |
| conversation_messages | id、conversation_id、role、content、created_at |

消息只永久保存 USER 和最终 ASSISTANT answer，角色有数据库约束。
外键关联用户/会话，索引支持用户会话列表和按会话加载消息。
title 使用第一条用户消息去除首尾空白后截取最多 100 个 Unicode 字符，不调用 LLM。
不存储 system prompt、内部 tool_calls、完整 ToolResult 或 Trace。
用户提交的消息和模型最终回答按原文持久化。

## API

所有接口都要求 Authorization: Bearer JWT。

首次请求 POST /api/agent/chat：

```json
{"message":"SKU-A001 库存怎么样？"}
```

成功响应直接返回：

```json
{
  "conversationId": 1,
  "answer": "模型根据数据生成的回答",
  "toolCalls": [
    {"iteration":1,"toolName":"get_inventory","arguments":{"sku":"SKU-A001"},"success":true}
  ]
}
```

续聊时使用返回的 ID：

```json
{"conversationId":1,"message":"那它最近 7 天卖得怎么样？"}
```

不传或传 null 每次都新建会话；conversationId 如提供必须为正数。
message 仍要求非空且最多 10000 字符。
不存在和其他用户的会话都返回 404，不自动替换为新会话。

- GET /api/conversations?limit=20&offset=0：当前用户会话列表，按 updated_at、id 倒序。
- GET /api/conversations/{id}/messages?limit=100&offset=0：该会话消息按 id 正序。

两个 GET 接口沿用 ApiResponse 外层，列表位于 data。
limit 允许 1–100，offset 必须非负；可分页取消息，避免一次返回全部历史。
会话列表返回 id、title、createdAt、updatedAt；消息返回 id、role、content、createdAt。
响应不添加 userId 或 JWT。未实现可选的删除接口。

## History 加载

配置 AGENT_MAX_HISTORY_MESSAGES，默认 20，允许 1–100。
计数包括本次刚保存的 USER 消息，不包括 system prompt 和本轮内部 Tool 消息。
数据库使用 conversationId + currentUserId 过滤，ORDER BY message.id DESC LIMIT N，
再将至多 N 条结果反转为升序传给模型；同一时间戳按 id 保持明确顺序。
本次 user 消息不会重复添加。

只截断发送给模型的窗口，不删除旧记录。旧消息仍可通过消息 API 分页读取。
窗口可能从 ASSISTANT 消息开始；这是普通文本消息，不存在截断 tool_call 配对的问题。
若先前提到的 SKU 已超出窗口，模型应请求澄清，不能保证恢复被截断的信息。
消息数量限制不是 token 限制，本阶段不做摘要或向量检索。

## 复用 Agent Loop

ConversationService 负责身份、保存和历史加载；AgentService 增加接收历史的入口，
内部继续使用原有唯一的 Loop。原单消息入口用于已有内部调用/回归，委托给同一 Loop，
HTTP 入口统一经过 ConversationService。

流程：

1. 获取 JWT 当前用户，创建会话或校验并锁定已有会话。
2. 保存当前 USER 消息，加载最近 N 条 USER/ASSISTANT。
3. 加上当前 system prompt，调用现有 Agent Loop。
4. 同轮多个 Tool 或连续多轮 Tool 的 assistant/tool 消息仍在本次内存中完整保留。
5. 保存最终 ASSISTANT answer，更新会话时间，返回 conversationId、answer 和本次 Trace。

每次 HTTP chat 的 iteration 从 1 开始，仍受 AGENT_MAX_ITERATIONS 限制。
达到正常迭代上限时，明确的上限提示作为本轮最终回答保存。
Prompt 要求结合历史理解指代；历史回答可能过时，最新数据仍需重新查询。
内部 Tool 消息不会带入下一次 HTTP 请求。

## 一致性与失败

chat 不使用覆盖整轮的数据库事务。准备阶段以短事务创建/校验会话、保存 USER 并加载历史，
事务提交后执行 LLM / Tool Calling / Agent Loop；最终保存 ASSISTANT 和更新时间使用另一短事务。
SELECT FOR UPDATE 仅用于数据库读写阶段，模型调用期间不持有会话行锁或事务绑定的连接。
没有新增并发调度机制，同一会话应由客户端顺序发起请求。

准备阶段异常会回滚该短事务。模型或最终保存异常时，另一个短事务清理本轮 USER，
首次创建的会话仅在没有剩余消息时移除；之前成功提交的历史保留。
这保留正常异常路径的失败轮次清理，但进程崩溃或清理期间数据库不可用时无法保证清理完成。
错误沿用现有脱敏 HTTP 响应。
客户端收到失败后可以重试；本阶段没有请求幂等键，网络中断后的重试可能产生重复轮次。

## 用户隔离

会话所有权仅来自 CurrentUserContext 的 JWT 用户。
续聊、查消息、写消息、更新时间均使用 conversationId + currentUserId，
会话列表只查询当前 user_id。LLM 只接收 role/content，不接收数据库用户 ID 或会话实体。
现有 ToolExecutor → CurrentUserContext → Service 的业务隔离不变。
即使模型或用户知道其他 conversationId，也不能通过这些接口读取或续聊该会话。

## 测试与边界

ConversationIntegrationTest 使用 FakeAgentChatModel 和独立 H2 MySQL 兼容数据库，
执行相同 Flyway V1–V4，新增 10 个测试：
首次创建、历史续聊、窗口截断、多轮工具回填、跨用户访问隔离、会话间隔离、
失败回滚、标题/配置校验、完整 JWT API 和跨用户 API 拒绝。
窗口测试将配置设为 3，验证 SQL 限制、时间顺序和旧记录保留。
Stage 3 API 断言更新为 3 个响应字段，并断言 conversationId 存在；其余原回归保留。

验证命令：

```powershell
git diff --check
cd backend
mvn test
```

事务边界修正后共 55/55 通过：原有 54 个 + 1 个事务边界测试，
验证新会话和续聊中 Tool 前后的模型调用均不处于事务内，也不绑定数据库连接。
自动测试不调用 DashScope；Fake 能验证历史传递和工具链，不验证真实模型的指代消解质量。
本次未做真实 DashScope 或真实 MySQL V4 联调。
现有 Maven toolchains 本机配置警告仍在，不影响 BUILD SUCCESS。

仅实现会话消息持久化和有限历史窗口，没有长期画像、向量 Memory、RAG、
写操作 Tool、Human Approval、Audit Log、MCP、Multi-Agent 或前端改动。

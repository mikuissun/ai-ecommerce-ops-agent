# Stage 6：写操作 Tool、Human Approval 与 Audit Log

## 单商品改价

保留现有 6 个只读 Tool，新增 update_product_price(sku, newPrice)。
ToolDefinition 继续转换为模型可见的 Function Schema；新增 NUMBER 类型支持价格，
应用使用 BigDecimal 处理金额。newPrice 必须大于 0，最多两位小数，
且不超过 DECIMAL(19,2) 可存范围；不静默四舍五入。
SKU 必须存在且属于当前 JWT 用户。

模型调用此 Tool 仅代表提议，不代表批准，也不会修改价格。
AgentService 检查 Tool.requiresApproval()，校验参数并创建 Pending Action 后，
立即结束本轮 Loop，返回服务端生成的确认提示。
如果同一响应有多个写调用，拒绝批量提议并将失败结果回填；
只读调用仍复用原有 Loop。合法写提议生成后，同一响应尚未执行的后续调用不再执行。
本阶段不增加第二套 Agent、批量修改或其他写 Tool。

## 为什么 LLM 无法直接执行

- ToolExecutor 对 requiresApproval 的 Tool 返回 APPROVAL_REQUIRED。
- UpdateProductPriceTool.execute 本身也只返回 APPROVAL_REQUIRED，不包含数据库写入。
- /api/tools/update_product_price/execute 调试入口同样无法改价。
- 不向模型注册 approve/reject Tool；聊天中说“确认”不执行批准。
- 真实写入仅由用户调用审批 API 后，通过 PendingActionService → ProductService.updatePrice 执行。

ProductService 在审批事务中按 currentUserId + sku 锁定商品，更新价格并返回实际修改前后值。
SQL 位于 Mapper/Service 数据访问层，Tool 不直接写 SQL。
Prompt 是行为提示，不作为安全边界；上述服务端检查才决定能否执行。

## Pending Action

Flyway V5__create_pending_actions_and_audit_logs.sql 新增 pending_actions：
id、user_id、conversation_id、tool_name、arguments_json、status、created_at、executed_at。
参数只保存通过校验的 sku/newPrice，审批接口不接受替换参数。
创建操作时校验会话归属；写提议不接受来自模型的 userId 或 conversationId 参数，
conversationId 由当前已验证的会话调用链传入。

状态：

- PENDING → APPROVED → EXECUTED：批准、更新价格并记录成功审计；APPROVED 是事务内中间状态。
- PENDING → REJECTED：拒绝，不修改商品。
- PENDING → APPROVED → FAILED：批准时参数失效或商品不存在等可预期业务失败，记录失败审计。

只有 PENDING 可 approve/reject。所有终态再次处理均返回 409。
审批短事务锁定当前用户的 Pending Action 行，防止两个并发批准重复执行。
没有分布式锁。价格更新、EXECUTED 状态和成功 Audit Log 在同一事务提交；
数据库/审计写入异常回滚整个批准事务，保持 PENDING，避免商品已改而缺少审计。
对这类基础设施故障不尝试在失败事务内伪造成功或失败记录。

## API

所有 API 均需 Authorization: Bearer JWT。

POST /api/agent/chat：

```json
{"message":"把 SKU-A001 的价格改成 25.99"}
```

写提议响应示例：

```json
{
  "conversationId": 1,
  "answer": "准备将 SKU-A001 的价格修改为 25.99，请确认是否执行。当前尚未修改价格。",
  "toolCalls": [
    {"iteration":1,"toolName":"update_product_price","arguments":{"sku":"SKU-A001","newPrice":25.99},"success":true}
  ],
  "pendingActionId": 1,
  "requiresApproval": true
}
```

此时 Trace success 表示提议成功创建，不是价格已经修改。
只读响应保留原结构；没有提议时省略 pendingActionId 和 requiresApproval。
Conversation Memory 保存确认提示并透传审批字段，模型调用依然在数据库长事务之外。

POST /api/pending-actions/{id}/approve：无须请求体，只执行数据库保存的参数。

```json
{
  "pendingActionId": 1,
  "status": "EXECUTED",
  "result": {"sku":"SKU-A001","beforePrice":19.99,"afterPrice":25.99},
  "errorMessage": null
}
```

POST /api/pending-actions/{id}/reject：返回 pendingActionId、status=REJECTED，
result 和 errorMessage 为 null，不修改价格。
可预期执行失败返回 status=FAILED 和脱敏错误信息，调用方必须检查 status。
无 JWT 返回 401；他人或不存在的操作返回 404；重复批准/拒绝返回 409。
响应不返回 userId、JWT、API Key 或内部异常堆栈。

## Audit Log

新增 audit_logs：
id、pending_action_id、user_id、action_type、target、before_value、after_value、status、created_at。
pending_action_id 为唯一键，确保每个执行操作最多一条审计结果。
成功记录 action_type=update_product_price、SKU、实际旧价格、新价格、SUCCESS。
预期业务失败记录 SKU 和 FAILED，未知前后值为 null。
拒绝不属于实际写执行，本阶段不额外记录拒绝审计。
不保存 JWT、API Key、system prompt 或异常堆栈，也不新增审计查询 API。

## 用户隔离与边界

Pending Action 的创建和处理只从 CurrentUserContext 获取用户身份；
审批查询使用 id + user_id，ProductService 查询和更新继续带当前 user_id 条件。
模型和审批请求都不能用参数替换执行用户。
beforePrice 是批准执行时读取的价格，而不是提议时快照；不提供过期检测或回滚改价功能。
不同待审批操作可以依次批准，同一操作不允许重复执行。
Pending Action 创建和会话最终回答保存使用不同短事务；若网络或保存失败，
可能存在已创建但未收到响应的待审批记录，它不会自动执行。

没有批量写操作、库存修改、订单取消/退款、复杂权限系统、MCP、Multi-Agent 或前端改动。
本阶段不实现超时自动批准，所有执行都必须经过审批 API。

## 测试

ApprovalIntegrationTest 使用 FakeAgentChatModel 和独立 H2 MySQL 兼容库，
执行相同 Flyway V1–V5。新增 14 个测试覆盖：
创建后不改价、响应字段、批准及审计、拒绝、跨用户处理、重复批准、
非法价格、执行失败审计、普通/直接/调试 Tool 绕过防护、userId 注入、
批量拒绝、JWT API、并发批准和审计故障回滚。

Stage 1–5 原有 55 个回归保留，仅调整注册 Tool 总数从 6 到 7 的断言。
本次 69/69 通过，所有自动测试均不调用真实 DashScope。

```powershell
git diff --check
cd backend
mvn test
```

本次未做真实 DashScope 或真实 MySQL V5 联调。
既有 Maven toolchains 本机配置警告仍在，不影响 BUILD SUCCESS。

# Stage 3：Qwen Tool Calling + Agent Loop

## 配置和 Qwen 接入

`AgentChatModel` 接收 messages 和 tools，返回一个 assistant message（文本或 tool_calls）。
`QwenChatModel` 使用 Spring RestClient 调用 DashScope OpenAI Compatible
`https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`，这是当前固定的中国内地接入地址。
API Key 必须匹配该地域；其他地域的接入地址尚未做配置支持。
请求使用非流式输出，关闭 thinking，tool_choice 为 auto。

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| DASHSCOPE_API_KEY | 无 | 仅通过 System.getenv 读取；不从配置文件或请求体读取 |
| AGENT_MODEL | qwen-plus | 模型名称 |
| AGENT_MAX_ITERATIONS | 6 | 单请求模型调用次数，允许 1–6，越界启动失败 |

没有 API Key 时其他业务接口仍可启动；Agent 请求返回脱敏的 503。
HTTP 连接超时为 10 秒，读取超时为 60 秒，不自动重试。
上游 HTTP 错误、超时或无效响应返回脱敏 502，不暴露响应体、密钥或堆栈。

接口协议参考：[阿里云 Chat Completions 文档](https://www.alibabacloud.com/help/en/model-studio/qwen-api-via-openai-chat-completions)。

## Tool Schema

`ToolSchemaConverter` 从现有 ToolRegistry 读取全部 ToolDefinition，
转换为 `type: function`、function.name、description 和 parameters。
ToolParameterSchema 的类型转换为 JSON Schema string/integer；日期加 format: date，
枚举加 enum，数值范围映射 minimum/maximum，必填项汇总为 required。
additionalProperties 为 false，服务端仍由 Stage 2 参数校验器实际校验。
没有维护第二套 Tool 定义，也没有添加新的业务 Tool。

## Agent Loop 与结果回填

1. 校验当前用户，创建本次请求独立的 system/user messages。
2. 将 messages 和六个 Tool Schema 发送给模型。
3. 有普通文本且无 tool_calls 时，返回 answer。
4. 有 tool_calls 时保留 assistant 调用消息，解析各 function.arguments JSON 对象。
5. 按模型返回顺序调用 ToolExecutor，追加 role=tool 的消息。
6. 每条消息保留原始 tool_call_id，content 为完整 ToolResult 的 JSON 字符串。
7. 全部结果回填后再次调用模型，直到最终回答或达到上限。

Tool 不存在、参数缺失/非法、JSON 损坏、业务查询失败和执行异常均成为失败 ToolResult；
模型可以基于失败解释原因或修正参数。缺少/重复 call id 等无法正确配对的协议错误返回脱敏 502。
最多调用模型 6 次（包括首次和生成最终答案的调用），多 Tool Calls 算一次模型调用。
第六次若仍要求 Tool，会顺序执行并记录，然后返回明确的上限提示，不发起第七次调用，
也不把未经模型总结的结果冒充最终回答。

所有调用同步顺序执行，保持 ThreadLocal CurrentUserContext。
每次 API 请求都创建新的消息列表，不存储跨请求历史。

## 用户隔离

调用链为 AgentService → ToolExecutor → CurrentUserContext → 业务 Service → 数据库。
身份来自 JWT Filter；模型不能指定或切换 userId。Tool Schema 不包含 userId，
Stage 2 校验器拒绝额外 userId 参数。查询保留现有 user_id 条件，不通过 HTTP 调用 /api/tools。
模型只接收业务 ToolResult，不接收 JWT 或用户身份字段。

## API

`POST /api/agent/chat`，需 `Authorization: Bearer <token>`。
message 必填且不能全为空白，最长 10000 字符。

```json
{"message":"SKU-A001 当前库存怎么样？"}
```

成功响应直接返回以下结构（不加 ApiResponse 外层）：

```json
{
  "answer": "模型根据库存 ToolResult 生成的回答",
  "toolCalls": [{"toolName":"get_inventory","success":true}]
}
```

toolCalls 是当前请求的简单执行记录，不返回参数、原始模型消息、system prompt、
JWT、userId 或堆栈。失败 HTTP 响应沿用现有 ApiResponse。

## 自动测试

运行：

```powershell
git diff --check
cd backend
mvn test
```

AgentIntegrationTest 使用 FakeAgentChatModel 和真实 ToolExecutor/Service/H2 数据库，
覆盖直接回答、库存/销售、多个 Tool、完整结果及 ID 回填、未知 Tool、非法参数与 JSON、
执行异常、纠错后重试、迭代上限、无跨请求历史、用户隔离、Schema 和 JWT API。
使用独立 H2 数据库避免污染 Stage 1–2 回归数据。
QwenChatModelTest 用 MockRestServiceServer 校验 HTTP 请求/响应、缺失 Key 和脱敏错误。
所有自动测试均不会调用真实 DashScope，即使运行环境中存在 API Key。

## 少量手动联调

当前开发进程未配置 DASHSCOPE_API_KEY，未执行真实 DashScope 请求。
在启动后端的终端设置 Key，避免将真实 Key 写入文件或 shell 历史：

```powershell
$agentKey = Read-Host 'DashScope API Key' -AsSecureString
$env:DASHSCOPE_API_KEY = [System.Net.NetworkCredential]::new('', $agentKey).Password
$env:AGENT_MODEL = 'qwen-plus'
$env:AGENT_MAX_ITERATIONS = '6'
cd backend
mvn spring-boot:run
```

确认 MySQL 和数据库环境配置正确后，在另一个终端登录并测试：

```powershell
$agentLogin = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/login' -ContentType 'application/json' -Body '{"email":"demo@example.com","password":"password"}'
$agentHeaders = @{ Authorization = 'Bearer ' + $agentLogin.data.accessToken }
$agentBody = @{ message = 'SKU-A001 当前库存怎么样？' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/agent/chat' -Headers $agentHeaders -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($agentBody))
```

可替换为“最近 7 天销售情况怎么样？”或“有哪些低库存商品？”各验证一次。
核对 answer 与 toolCalls；不要打印或提交 Key/JWT。
根目录 .env 不会由 Java 自动加载为进程环境变量。

## 范围

本阶段仅实现 Tool Calling 核心闭环。没有 Planning、Memory、写操作 Tool、
Human Approval、MCP、Multi-Agent 或前端改动，不新增通用 Agent Framework。

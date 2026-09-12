# Stage 7：AI Operations Workspace

Stage 7 将已有 Stage 1–6 能力产品化为跨境电商 AI 运营工作台。后端 Agent 架构保持不变，前端通过既有 API 展示业务摘要、会话、聊天、Tool Trace 和 Human Approval。

## 页面结构

App 使用 Vue Router，AgentWorkspace 组织桌面优先的三栏 Workspace：

- 顶部品牌栏：Commerce Ops、Agent Ready、当前用户和退出。
- 左侧 ConversationSidebar：新建会话、当前用户会话列表、更新时间和空状态。
- 中间 Workspace：Copilot 标题、Dashboard 摘要、Welcome Prompt、消息、输入框和 Approval Card。
- 右侧 ToolTracePanel：按 iteration 展示工具、参数和成功/失败状态。

组件拆分为 LoginView、ConversationSidebar、OperationsSummary、ChatMessage、PromptSuggestions、ToolTracePanel 和 ApprovalCard。1280px/1440px 桌面优先，小屏保留横向会话导航。

## 前端 API 与状态

frontend/src/api.ts 提供统一 Axios 封装，自动附加 JWT，401 时清理凭证并回到 /login。开发服务器使用同源 /api 代理，修复浏览器跨域登录失败。

前端不保存模型上下文作为长期状态。conversationId 来自服务端；切换会话通过消息 API 重新加载，续聊把同一个 ID 发回 /api/agent/chat。USER/ASSISTANT 历史来自数据库，首次会话由服务端创建。

## Dashboard、Chat 与 Trace

顶部摘要调用 /api/dashboard/summary，展示商品数、低库存数、7 日订单数和原始销售额合计。后端未做跨币种换算，因此显示 mixed FX，不加美元符号。聊天支持快捷 Prompt、安全 Markdown 表格、多行消息、IME 输入、Enter 发送、loading、错误保留草稿、自动滚动和重复发送保护；进行请求时禁止切换会话。

Agent 返回的 toolCalls 进入 Trace，展示 iteration、toolName、参数白名单和 success；不渲染 userId、JWT、system prompt、API Key、完整 ToolResult 或 stack trace。写调用标为 PROPOSED，不误报已经执行。历史 Trace 不持久化，界面明确提示；新回复没有工具时才显示 No tools were required for this response.

## Multi-Tool 与 Memory

“找出低库存商品，并结合最近 7 天销量给出补货建议。”会在 Trace 中显示多个 Tool，按服务端返回顺序和 iteration 渲染，再显示最终回答。

“SKU-A001 当前库存怎么样？”后继续发送“那它最近 7 天卖得好吗？”时，前端携带同一个 conversationId。切换会话再切回来，会从 Conversation API 恢复消息。

## Human Approval

当响应包含 requiresApproval=true 和 pendingActionId 时，显示专用 ApprovalCard，展示 SKU、提议价格和批准前不会改变的说明。Approve/Reject 有 loading 和禁用状态，调用既有审批 API，成功后卡片内显示 Executed 或 Rejected。

前端没有 Product update API，也不把聊天中的文字确认当作审批。价格变更仍必须走 Pending Action 和 JWT 保护的审批接口。

保留最小只读补充 `GET /api/pending-actions?conversationId=...&limit=100&offset=0`，查询必须匹配当前 JWT 用户，会话不存在或跨用户返回 404。分页恢复真实 PENDING/EXECUTED/REJECTED/FAILED 卡片；错误时保留卡片与重试按钮，执行后展示真实 before/after。未增加 Tool 或数据库表。

## E2E

真实联调链路：Login → Workspace → Agent Chat → Conversation → qwen-plus → Tool Calling → MySQL → Tool Trace → Final Answer。

写操作链路：价格请求 → qwen-plus → Pending Action → Approval Card → Approve → ProductService → MySQL → Audit Log。

2026-09-11～12 验收：根目录 .env 已由原有启动脚本读取，Key 未打印或写入前端。真实 qwen-plus Single Tool 成功，返回 5 款低库存商品；刷新/切换后从 MySQL 恢复 USER/ASSISTANT 消息成功。

随后 Multi-Tool 请求被 DashScope HTTP 400 拒绝，提供方指向账户欠费，用户确认余额不足。已停止真实调用；Memory 指代续聊和 Approval + Audit Log 尚未执行，不能宣称四组通过。恢复计费后继续下面第 2～4 组：

1. 有哪些低库存商品？
2. 找出低库存商品，并结合最近 7 天销量给出补货建议。
3. 同一会话先问 SKU-A001 当前库存怎么样？，再问 那它最近 7 天卖得好吗？
4. 把 SKU-A001 的价格改成 25.99，在 Approval Card 中 Approve/Reject。

## 验证与边界

后端 71/71（原 69 + 2 个恢复/权限回归），前端 7/7 组件测试通过；前端 build 通过。Vite 大 bundle warning 保留。1440×1000 与 1280×900：页面宽度等于视口宽度，输入区位于视口内；Console 未发现 JS runtime error，服务故障显示在 UI。

真实截图：`images/operations-workspace.png`、`images/single-tool.png`、`images/workspace-1280.png`，README 已引用。Single Tool 截图为从数据库恢复后的视图，不伪造历史 Trace。其余三组截图待真实验证。

启动注意：本机已有数据卷使用原开发密码，新 .env MYSQL_PASSWORD 与之不同；本次仅用进程 DB_PASSWORD 覆盖连接已有库，未修改 .env 或数据库账号、未清空数据。后续启动仍需提供匹配已有库的连接密码。Qwen 仅保留异常类型/HTTP 状态诊断日志，临时提供方正文诊断已移除。

未解决项：DashScope 余额阻断其余真实 E2E；会话消息的无时区时间戳与即时显示存在时差（不影响 Memory 内容）；最新历史 Trace 仅会话内缓存，不做数据库持久化。当前不视为最终验收完成。

本阶段不添加 MCP、Multi-Agent、新 Planning、向量 Memory、RAG、新复杂 Tool、批量写操作、库存/订单写操作、WebSocket、SSE 或大型前端状态框架。

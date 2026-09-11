# Stage 2：只读 Tool System

## 目标与边界

Stage 2 在 Stage 1 的业务 Service 之上提供统一的只读 Tool 执行层，链路为：

```text
Business Service -> Tool -> ToolRegistry -> ToolExecutor -> ToolResult
```

本阶段不包含 LLM、qwen-plus、Agent Loop、Function Calling、Planning、Memory、MCP、Multi-Agent、写入 Tool 或 Human Approval。Tool 不直接写 SQL，也不通过 HTTP 调用自身 API。

## 核心抽象

`backend/src/main/java/com/mikuissun/ecommerceagent/tool/` 包含：

- `ToolDefinition`：Tool 名称、描述和参数定义。
- `ToolParameterSchema`：参数类型、是否必填、枚举值和数值范围。
- `ToolResult`：统一的成功/失败结果，包含 `success`、`data`、`errorCode` 和 `errorMessage`。
- `Tool`：Tool 定义与执行入口。
- `ToolRegistry`：自动发现 Spring `Tool` Bean，按唯一名称注册并提供稳定排序的定义列表。
- `ToolExecutor`：按名称查找、统一校验参数、读取当前用户身份并执行 Tool。

参数校验统一支持 `string`、`integer`、`date`、`enum`，会拒绝未知参数、缺失必填参数、非法日期、非法枚举值和越界整数。当前用户只能从 `CurrentUserContext` 获取，Tool 参数不接受 `userId`。

## 已实现 Tool

| Tool | 参数 | Service 调用 |
|---|---|---|
| `get_product` | `sku`（必填） | `ProductService` |
| `search_products` | `keyword`、`marketplace`、`status` | `ProductService` |
| `get_inventory` | `sku`（必填） | `ProductService`、`InventoryService` |
| `list_low_stock_products` | `marketplace` | `InventoryService`、`ProductService` |
| `query_orders` | `status`、`marketplace`、`startDate`、`endDate` | `OrderService` |
| `get_sales_summary` | `days`，默认 7，范围 1-90 | `SalesSummaryService` |

订单状态和商品状态使用定义中的枚举约束；查询结果带有固定上限，避免调试或后续调用一次读取过量数据。销售汇总由 Service 组合订单统计和热销商品聚合数据。

## 用户隔离

所有 Tool 执行都要求 JWT。`ToolExecutor` 从 `CurrentUserContext` 获取用户 ID，底层 Service 查询同时带用户条件。商品、库存、订单和销售汇总均不能读取其他用户数据；跨用户资源按现有业务约定返回未找到或隔离后的结果。

## 调试 API

Stage 2 提供用于人工验证的 JWT 保护调试 API：

- `GET /api/tools`：返回已注册 Tool 定义。
- `POST /api/tools/{toolName}/execute`：请求体为 `{ "arguments": { ... } }`，返回统一 `ToolResult`。

该 API 仅用于验证 Tool System，不代表已接入 LLM 或 Agent Loop。

## 测试与验证

`ToolSystemIntegrationTest` 覆盖：Registry 自动发现和重复名称保护、参数校验、6 个 Tool 的执行、Service 复用、日期/状态过滤、销售汇总、调试 API JWT 保护以及跨用户隔离。Stage 1 原有 `EcommerceAgentIntegrationTest` 继续作为回归测试。

```powershell
git diff --check
cd backend
mvn test
```

Stage 2 完成后仍保持工作区由用户自行检查、暂不自动执行 `git add`、`git commit` 或 `git push`。

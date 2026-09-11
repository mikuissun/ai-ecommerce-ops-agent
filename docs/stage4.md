# Stage 4：多工具任务分析与运营建议

## 多 Tool 任务

沿用 Stage 3 AgentService、AgentChatModel、ToolRegistry 和 ToolExecutor。
模型可以在同一响应中发起多个 tool_calls，也可以先查询库存，再根据结果继续查询销售。
同一响应中的调用按顺序执行，全部结果回填后再次请求模型。
每轮 assistant tool_calls 和对应 role=tool 消息都保留在本次请求中，
通过原始 tool_call_id 关联。失败 ToolResult 同样回填，供模型修正参数或说明不足。
AGENT_MAX_ITERATIONS 继续控制模型调用次数（默认及硬上限为 6）。

| 运营问题 | 所需数据/调用 |
|---|---|
| 有哪些低库存商品 | list_low_stock_products |
| 最近 7 天销售情况 | get_sales_summary(days=7) |
| 低库存与补货建议 | list_low_stock_products + get_sales_summary；需要时按 SKU 补查销量 |
| SKU-A001 库存及销量 | get_inventory(sku) + get_sales_summary(sku, days=7) |
| 近期退款及销售 | query_orders(status=REFUNDED, startDate, endDate) + get_sales_summary(days=7) |

这些是 Prompt 引导的任务组合，不是 Java 按用户文本写死的路由或建议。
模型负责选择调用和生成最终 answer。

## 最小销售能力扩展

Tool 数量仍为 6。原 get_sales_summary 仅提供热销前 10，未上榜不能代表零销量，
因此新增可选 sku 参数；不传 sku 时原汇总响应不变。
传 sku 时查询当前用户的指定商品，并返回：

```json
{
  "sku": "SKU-A001",
  "days": 7,
  "unitsSold": 4,
  "revenue": 40,
  "orderCount": 2,
  "currency": "USD"
}
```

以上仅为结构示例。days 默认 7，范围 1–90。
单品统计范围是服务端当前时间向前 days 天，排除未来订单；
仅计 PAID、SHIPPED、COMPLETED 状态，排除退款、取消和未支付订单。
unitsSold 按订单明细数量合计，revenue 按 subtotal 合计，
orderCount 为不同订单数，同一订单多个明细不重复计数。
商品存在但无成交返回 0；不存在或属于其他用户返回 NOT_FOUND。
currency 使用商品币种，沿用现有数据的订单/商品币种一致约定，不实现汇率换算。
原全店汇总没有新增多币种分组；不能把跨币种金额直接作统一币种结论。

SQL 只在 Mapper 中实现；Tool → SalesSummaryService → ProductService 校验归属，
聚合查询再使用用户和商品条件。模型不能传 userId。
Schema 仍从现有 ToolDefinition 自动生成，增加 sku 不引入第二份定义。

## 运营建议

Prompt 要求结合相关业务数据，区分事实和建议，说明周期，给出简洁可执行的建议。
热销榜缺失 SKU 时不能推断零销量；需要时补查单品。
没有采购周期、目标库存或历史对比时，不编造精确补货量或增长率。
缺失/失败的数据明确说明，不声称执行修改。
系统提示包含当前服务端日期，供模型构造近期订单日期参数。
订单日期接口使用自然日，销售接口使用滚动时间窗口，边界时刻可能不同；
模型应说明实际查询周期，不能把两者强行视为完全相同的统计口径。
Java 没有增加补货阈值决策、独立 Planner 或固定建议模板。

## Agent Trace

POST /api/agent/chat 的入口及 answer 保持不变，toolCalls 扩展为：

```json
{
  "answer": "模型结合查询结果生成的事实与建议",
  "toolCalls": [
    {"iteration":1,"toolName":"get_inventory","arguments":{"sku":"SKU-A001"},"success":true},
    {"iteration":2,"toolName":"get_sales_summary","arguments":{"sku":"SKU-A001","days":7},"success":true}
  ]
}
```

iteration 从 1 开始，表示发起调用的模型轮次；同一轮多个调用拥有相同 iteration。
arguments 只保留已注册参数名的短字符串/整数；忽略未知字段、嵌套对象、
过长字符串及明显的认证信息。非法 JSON 或未知 Tool 的 trace 参数为空。
参数值用于说明模型尝试的调用，不代表校验已通过，需同时看 success。
不返回原始参数文本、userId、JWT、system prompt 或完整 ToolResult；
完整 ToolResult 只用于本次模型消息回填，不持久化 Trace 或聊天历史。

## 测试与联调

复用 FakeAgentChatModel + 真实 ToolExecutor/Service/H2 测试。
新增 9 个测试，覆盖上述任务、两轮调用及全量回填、同轮多调用、
失败后继续、Trace 字段过滤、配置迭代上限、精确销售聚合和同名 SKU 用户隔离。
聚合测试使用相对当前时间的订单，并回滚测试数据。

本次验证结果：Stage 1 的 7 个、Stage 2 的 11 个、Stage 3 的 17 个回归全部通过；
Stage 4 新增 9 个通过，总计 44 个。

```powershell
git diff --check
cd backend
mvn test
```

自动测试不调用 DashScope。Fake 测试验证调用编排、数据和回填链路，
不能证明真实模型一定选择预期 Tool 或给出高质量建议。
当前进程未配置 DASHSCOPE_API_KEY，因此未进行真实联调。
配置方式及 JWT 请求示例见 stage3.md；手动只需将 message 替换为：

- 找出低库存商品，并结合最近 7 天销量给出补货建议。
- SKU-A001 当前库存怎么样？最近卖得好吗？

各调用一次，检查 toolCalls 确有两个相关 Tool，轮次正确，answer 区分事实与建议。

## 当前边界

只读运营分析，不含长期 Memory、写操作 Tool、Human Approval、MCP、
Multi-Agent、前端或独立复杂 Planner。达到迭代上限仍返回明确提示。
原有 Maven toolchains 本机配置警告仍存在，不影响本次 BUILD SUCCESS。

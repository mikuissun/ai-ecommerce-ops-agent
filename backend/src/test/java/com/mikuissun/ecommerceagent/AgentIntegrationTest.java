package com.mikuissun.ecommerceagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.ecommerceagent.agent.*;
import com.mikuissun.ecommerceagent.common.*;
import com.mikuissun.ecommerceagent.dto.auth.RegisterRequest;
import com.mikuissun.ecommerceagent.service.*;
import com.mikuissun.ecommerceagent.tool.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:agent_stage3;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AgentIntegrationTest.Config.class)
class AgentIntegrationTest {
    @TestConfiguration
    static class Config {
        @Bean @Primary FakeAgentChatModel fakeAgentChatModel() { return new FakeAgentChatModel(); }
    }
    static class FakeAgentChatModel implements AgentChatModel {
        final Deque<JsonNode> replies = new ArrayDeque<>();
        final List<List<JsonNode>> requests = new ArrayList<>();
        Runnable beforeChat = () -> {};
        List<JsonNode> tools;
        @Override public JsonNode chat(List<JsonNode> messages, List<JsonNode> tools) {
            beforeChat.run();
            requests.add(messages.stream().map(node -> (JsonNode) node.deepCopy()).toList());
            this.tools = tools;
            return replies.removeFirst();
        }
        void reset() { replies.clear(); requests.clear(); tools = null; beforeChat = () -> {}; }
    }

    @Autowired FakeAgentChatModel fake;
    @Autowired AgentService agent;
    @Autowired ObjectMapper json;
    @Autowired ToolRegistry registry;
    @Autowired ToolExecutor executor;
    @Autowired ToolSchemaConverter schemas;
    @Autowired UserService users;
    @Autowired MockMvc mvc;

    @BeforeEach void setup() { fake.reset(); CurrentUserContext.setUserId(1L); }
    @AfterEach void cleanup() { CurrentUserContext.clear(); }

    @Test void directAnswerDoesNotExecuteTools() {
        reply("你好");
        var result = agent.chat("你好");
        assertEquals("你好", result.answer());
        assertTrue(result.toolCalls().isEmpty());
        assertEquals(1, fake.requests.size());
        assertEquals(7, fake.tools.size());
        assertEquals(2, fake.requests.get(0).size());
    }

    @Test void inventoryResultIsFedBackWithOriginalIdAndAssistantCall() throws Exception {
        calls(call("inventory-123", "get_inventory", "{\"sku\":\"SKU-A002\"}"));
        reply("可用库存 5");
        var response = agent.chat("SKU-A002 当前库存怎么样？");
        assertEquals("可用库存 5", response.answer());
        assertTrue(response.toolCalls().get(0).success());
        var messages = fake.requests.get(1);
        assertEquals("inventory-123", messages.get(2).path("tool_calls").get(0).path("id").asText());
        assertEquals("inventory-123", messages.get(3).path("tool_call_id").asText());
        assertEquals("tool", messages.get(3).path("role").asText());
        assertEquals(5, feedback(3).path("data").path("availableQuantity").asInt());
    }

    @Test void salesSummaryUsesRealService() throws Exception {
        calls(call("sales", "get_sales_summary", "{\"days\":7}"));
        reply("已汇总");
        agent.chat("最近 7 天销售情况");
        JsonNode result = feedback(3);
        assertTrue(result.path("success").asBoolean());
        assertTrue(result.path("data").has("orderCount"));
        assertTrue(result.path("data").has("revenue"));
        JsonNode expected = json.valueToTree(executor.execute("get_sales_summary", Map.of("days", 7)));
        assertEquals(json.readTree(expected.toString()), result);
    }

    @Test void multipleCallsExecuteInOrderBeforeNextModelRequest() throws Exception {
        calls(call("low", "list_low_stock_products", "{}"),
                call("sales", "get_sales_summary", "{\"days\":7}"));
        reply("库存与销售总结");
        var response = agent.chat("低库存和销售");
        assertEquals(List.of("list_low_stock_products", "get_sales_summary"),
                response.toolCalls().stream().map(r -> r.toolName()).toList());
        assertEquals(5, fake.requests.get(1).size());
        assertEquals("low", fake.requests.get(1).get(3).path("tool_call_id").asText());
        assertEquals("sales", fake.requests.get(1).get(4).path("tool_call_id").asText());
        assertTrue(feedback(3).path("success").asBoolean());
        assertTrue(feedback(4).path("success").asBoolean());
    }

    @Test void unknownMissingAndInvalidArgumentsAreReturnedAsFailures() throws Exception {
        calls(call("unknown", "absent", "{}"), call("missing", "get_inventory", "{}"),
                call("invalid", "get_sales_summary", "{\"days\":91}"),
                call("syntax", "get_inventory", "{"), call("array", "get_inventory", "[]"),
                call("null", "get_inventory", "null"),
                call("trailing", "get_inventory", "{} {}"));
        reply("部分查询失败");
        var result = agent.chat("查询");
        assertEquals(7, result.toolCalls().size());
        assertTrue(result.toolCalls().stream().noneMatch(r -> r.success()));
        assertEquals("TOOL_NOT_FOUND", feedback(3).path("errorCode").asText());
        assertEquals("MISSING_PARAMETER", feedback(4).path("errorCode").asText());
        assertEquals("PARAMETER_OUT_OF_RANGE", feedback(5).path("errorCode").asText());
        for (int index = 6; index <= 9; index++) {
            assertEquals("INVALID_ARGUMENTS", feedback(index).path("errorCode").asText());
        }
    }

    @Test void executionExceptionIsFedBackWithoutInternalDetails() throws Exception {
        Tool failing = new Tool() {
            public ToolDefinition definition() { return new ToolDefinition("failing", "test", List.of()); }
            public ToolResult execute(ToolArguments arguments) {
                throw new IllegalStateException("private database details");
            }
        };
        var localRegistry = new ToolRegistry(List.of(failing));
        var localAgent = new AgentService(fake, localRegistry,
                new ToolExecutor(localRegistry, new ToolArgumentValidator()), schemas, json, 6);
        calls(call("failure", "failing", "{}"));
        reply("查询失败");
        var response = localAgent.chat("查询");
        assertFalse(response.toolCalls().get(0).success());
        assertEquals("EXECUTION_ERROR", feedback(3).path("errorCode").asText());
        assertFalse(feedback(3).toString().contains("private database"));
    }

    @Test void modelCanCorrectArgumentsAfterFailure() throws Exception {
        calls(call("bad", "get_inventory", "{}"));
        calls(call("good", "get_inventory", "{\"sku\":\"SKU-A002\"}"));
        reply("库存 5");
        var result = agent.chat("库存");
        assertFalse(result.toolCalls().get(0).success());
        assertTrue(result.toolCalls().get(1).success());
        assertEquals(3, fake.requests.size());
    }

    @Test void iterationLimitStopsAtSixModelRequests() {
        for (int i = 0; i < 6; i++) calls(call("call-" + i, "get_inventory", "{}"));
        var result = agent.chat("持续查询");
        assertEquals(6, fake.requests.size());
        assertEquals(6, result.toolCalls().size());
        assertTrue(result.answer().contains("上限"));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentService(fake, registry, executor, schemas, json, 7));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentService(fake, registry, executor, schemas, json, 0));
    }

    @Test void newRequestDoesNotRetainPriorMessages() {
        reply("第一次");
        agent.chat("问题一");
        reply("第二次");
        agent.chat("问题二");
        assertEquals(2, fake.requests.get(1).size());
        assertEquals("问题二", fake.requests.get(1).get(1).path("content").asText());
    }

    @Test void modelCannotOverrideCurrentUserAndCannotReadDemoDataAsAnotherUser() throws Exception {
        var user = users.register(new RegisterRequest("agent-" + UUID.randomUUID() + "@example.com",
                "TestPassword1", "Agent Test"));
        CurrentUserContext.setUserId(user.getId());
        calls(call("override", "get_inventory", "{\"sku\":\"SKU-A002\",\"userId\":1}"),
                call("isolated", "get_inventory", "{\"sku\":\"SKU-A002\"}"),
                call("orders", "query_orders", "{}"),
                call("sales", "get_sales_summary", "{}"));
        reply("没有可用数据");
        agent.chat("查询 demo 数据");
        assertEquals("UNKNOWN_PARAMETER", feedback(3).path("errorCode").asText());
        assertEquals("NOT_FOUND", feedback(4).path("errorCode").asText());
        assertEquals(0, feedback(5).path("data").size());
        assertEquals(0, feedback(6).path("data").path("orderCount").asInt());
        assertEquals(user.getId(), CurrentUserContext.getUserId());
    }

    @Test void schemasComeFromRegistryAndPreserveConstraints() {
        Map<String, JsonNode> byName = new HashMap<>();
        for (JsonNode tool : schemas.convert(registry.definitions())) {
            assertEquals("function", tool.path("type").asText());
            var function = tool.path("function");
            byName.put(function.path("name").asText(), function.path("parameters"));
            assertFalse(function.path("parameters").path("properties").has("userId"));
            assertFalse(function.path("parameters").path("additionalProperties").asBoolean());
        }
        assertEquals(7, byName.size());
        assertEquals("sku", byName.get("get_inventory").path("required").get(0).asText());
        var days = byName.get("get_sales_summary").path("properties").path("days");
        assertEquals("integer", days.path("type").asText());
        assertEquals(1, days.path("minimum").asInt());
        assertEquals(90, days.path("maximum").asInt());
        var orders = byName.get("query_orders").path("properties");
        assertEquals("date", orders.path("startDate").path("format").asText());
        assertEquals(6, orders.path("status").path("enum").size());
    }

    @Test void malformedCallIdsFailCleanlyBeforeExecution() {
        calls(call("", "get_inventory", "{}"));
        assertThrows(BusinessException.class, () -> agent.chat("查询"));
        assertEquals(1, fake.requests.size());
    }

    @Test void apiRequiresJwtValidatesMessageAndReturnsOnlyAnswerAndTrace() throws Exception {
        CurrentUserContext.clear();
        mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"你好\"}")).andExpect(status().isUnauthorized());
        assertTrue(fake.requests.isEmpty());
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"demo@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(login).path("data").path("accessToken").asText();
        mvc.perform(post("/api/agent/chat").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\" \"}"))
                .andExpect(status().isBadRequest());
        calls(call("inventory", "get_inventory", "{\"sku\":\"SKU-A002\"}"));
        reply("可用库存 5");
        String body = mvc.perform(post("/api/agent/chat").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"库存？\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("可用库存 5"))
                .andExpect(jsonPath("$.toolCalls[0].success").value(true))
                .andReturn().getResponse().getContentAsString();
        assertEquals(3, json.readTree(body).size());
        assertTrue(json.readTree(body).path("conversationId").asLong() > 0);
        assertFalse(body.contains(token));
        assertFalse(body.contains("userId"));
        assertFalse(body.contains("system"));
        assertFalse(body.contains("stackTrace"));
        assertNull(CurrentUserContext.getUserId());
    }


    @Test void stage4LowStockTaskReturnsModelAdvice() throws Exception {
        calls(call("low", "list_low_stock_products", "{}"));
        reply("事实：已查询低库存。建议：优先核对补货周期。");
        var response = agent.chat("有哪些低库存商品？");
        assertTrue(feedback(3).path("data").isArray());
        assertEquals(1, response.toolCalls().get(0).iteration());
        assertEquals(Map.of(), response.toolCalls().get(0).arguments());
        assertTrue(response.answer().contains("建议"));
    }

    @Test void stage4ReplenishmentBatchPreservesBothResultsAndTrace() throws Exception {
        calls(call("low", "list_low_stock_products", "{}"),
                call("sales", "get_sales_summary", "{\"days\":7}"));
        String advice = "事实：低库存与近7天销量已查询。建议：核实采购周期后确定补货量。";
        reply(advice);
        var response = agent.chat("找出低库存商品，并结合最近 7 天销量给出补货建议。");
        assertEquals(advice, response.answer());
        assertEquals(List.of(1, 1), response.toolCalls().stream().map(r -> r.iteration()).toList());
        assertEquals(Map.of("days", 7), response.toolCalls().get(1).arguments());
        assertTrue(feedback(3).path("success").asBoolean());
        assertTrue(feedback(4).path("data").has("topSellingProducts"));
    }

    @Test void stage4InventoryThenSkuSalesRetainsEveryRound() throws Exception {
        calls(call("inventory", "get_inventory", "{\"sku\":\"SKU-A001\"}"));
        calls(call("sales", "get_sales_summary", "{\"sku\":\"SKU-A001\",\"days\":7}"));
        reply("事实：库存和近7天单品销量已查询；缺少同期历史，不能判断增长率。");
        var response = agent.chat("SKU-A001 当前库存怎么样？最近卖得好吗？");
        assertEquals(List.of(1, 2), response.toolCalls().stream().map(r -> r.iteration()).toList());
        var finalRequest = fake.requests.get(2);
        assertEquals(6, finalRequest.size());
        assertEquals(fake.requests.get(1).subList(0, 4), finalRequest.subList(0, 4));
        assertEquals("sales", finalRequest.get(5).path("tool_call_id").asText());
        var sales = json.readTree(finalRequest.get(5).path("content").asText()).path("data");
        assertEquals("SKU-A001", sales.path("sku").asText());
        assertEquals(7, sales.path("days").asInt());
        assertTrue(sales.has("unitsSold"));
        assertTrue(response.answer().contains("不能判断增长率"));
    }

    @Test void stage4RefundsAndSalesUseSameWindow() throws Exception {
        String end = java.time.LocalDate.now().toString();
        String start = java.time.LocalDate.now().minusDays(7).toString();
        calls(call("refunds", "query_orders",
                "{\"status\":\"REFUNDED\",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\"}"));
        calls(call("sales", "get_sales_summary", "{\"days\":7}"));
        reply("事实：已查询近期退款订单及销售；建议逐单核实退款原因。");
        var response = agent.chat("最近有哪些退款订单，销售情况怎么样？");
        for (JsonNode order : feedback(3).path("data")) assertEquals("REFUNDED", order.path("status").asText());
        assertEquals("query_orders", response.toolCalls().get(0).toolName());
        assertEquals("get_sales_summary", response.toolCalls().get(1).toolName());
        assertEquals(6, fake.requests.get(2).size());
    }

    @Test void stage4FailureStillAllowsOtherToolAndTraceRedactsUnknownFields() throws Exception {
        calls(call("bad", "get_inventory", "{\"sku\":\"SKU-A001\",\"userId\":2,\"JWT\":\"private\"}"),
                call("good", "get_sales_summary", "{\"days\":7}"));
        reply("库存查询失败，可提供销售结果。");
        var response = agent.chat("库存与销售");
        assertFalse(response.toolCalls().get(0).success());
        assertTrue(response.toolCalls().get(1).success());
        String trace = json.writeValueAsString(response.toolCalls());
        assertFalse(trace.contains("userId"));
        assertFalse(trace.contains("JWT"));
        assertFalse(trace.contains("private"));
        assertEquals(Map.of("sku", "SKU-A001"), response.toolCalls().get(0).arguments());
        assertEquals("UNKNOWN_PARAMETER", feedback(3).path("errorCode").asText());
    }

    @Test void stage4TraceOmitsSensitiveOrMalformedArgumentValues() throws Exception {
        calls(call("token", "get_inventory", "{\"sku\":\"Bearer private-token\"}"),
                call("nested", "get_inventory", "{\"sku\":{\"userId\":1}}"),
                call("broken", "get_sales_summary", "{"));
        reply("查询参数无效");
        var response = agent.chat("查询");
        assertTrue(response.toolCalls().stream().allMatch(r -> r.arguments().isEmpty()));
        assertFalse(json.writeValueAsString(response.toolCalls()).contains("private-token"));
    }

    @Test void stage4AlternatingToolsStillStopAtConfiguredLimit() {
        var limited = new AgentService(fake, registry, executor, schemas, json, 2);
        calls(call("stock", "list_low_stock_products", "{}"));
        calls(call("sales", "get_sales_summary", "{\"days\":7}"));
        var response = limited.chat("继续分析");
        assertEquals(2, fake.requests.size());
        assertEquals(List.of(1, 2), response.toolCalls().stream().map(r -> r.iteration()).toList());
        assertTrue(response.answer().contains("上限"));
    }

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void stage4SkuSalesAggregateStatusesDatesDistinctOrdersAndZeroSales() {
        long productId = insertProduct(1L, "STAGE4-SALES");
        long paid = insertOrder(1L, "PAID", java.time.LocalDateTime.now().minusDays(1));
        insertItem(paid, productId, "STAGE4-SALES", 2);
        insertItem(paid, productId, "STAGE4-SALES", 3);
        for (String status : List.of("SHIPPED", "COMPLETED")) {
            insertItem(insertOrder(1L, status, java.time.LocalDateTime.now().minusDays(2)), productId, "STAGE4-SALES", 1);
        }
        for (String status : List.of("REFUNDED", "CANCELLED", "PENDING")) {
            insertItem(insertOrder(1L, status, java.time.LocalDateTime.now().minusDays(1)), productId, "STAGE4-SALES", 100);
        }
        insertItem(insertOrder(1L, "PAID", java.time.LocalDateTime.now().minusDays(8)), productId, "STAGE4-SALES", 100);
        insertItem(insertOrder(1L, "PAID", java.time.LocalDateTime.now().plusDays(1)), productId, "STAGE4-SALES", 100);
        var result = executor.execute("get_sales_summary", Map.of("sku", "STAGE4-SALES"));
        assertTrue(result.success());
        var sales = json.valueToTree(result.data());
        assertEquals(7, sales.path("unitsSold").asInt());
        assertEquals(3, sales.path("orderCount").asInt());
        assertEquals(0, new java.math.BigDecimal("70").compareTo(sales.path("revenue").decimalValue()));
        assertEquals(7, sales.path("days").asInt());
        insertProduct(1L, "STAGE4-ZERO");
        var zero = json.valueToTree(executor.execute("get_sales_summary", Map.of("sku", "STAGE4-ZERO")).data());
        assertEquals(0, zero.path("unitsSold").asInt());
        assertEquals(0, zero.path("orderCount").asInt());
        assertEquals("NOT_FOUND", executor.execute("get_sales_summary", Map.of("sku", "ABSENT-SKU")).errorCode());
        assertEquals("PARAMETER_OUT_OF_RANGE", executor.execute("get_sales_summary",
                Map.of("sku", "STAGE4-ZERO", "days", 91)).errorCode());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void stage4SameSkuSalesAreIsolatedBetweenUsersThroughAgent() throws Exception {
        var other = users.register(new RegisterRequest("stage4-" + UUID.randomUUID() + "@example.com", "TestPass123", "Other"));
        long firstProduct = insertProduct(1L, "SHARED-STAGE4");
        long otherProduct = insertProduct(other.getId(), "SHARED-STAGE4");
        insertItem(insertOrder(1L, "PAID", java.time.LocalDateTime.now().minusDays(1)), firstProduct, "SHARED-STAGE4", 4);
        insertItem(insertOrder(other.getId(), "PAID", java.time.LocalDateTime.now().minusDays(1)), otherProduct, "SHARED-STAGE4", 9);
        CurrentUserContext.setUserId(other.getId());
        calls(call("sales", "get_sales_summary", "{\"sku\":\"SHARED-STAGE4\"}"));
        reply("本账户销量 9");
        agent.chat("单品销量");
        assertEquals(9, feedback(3).path("data").path("unitsSold").asInt());
        CurrentUserContext.setUserId(1L);
        assertEquals(4, json.valueToTree(executor.execute("get_sales_summary",
                Map.of("sku", "SHARED-STAGE4")).data()).path("unitsSold").asInt());
        assertEquals("UNKNOWN_PARAMETER", executor.execute("get_sales_summary",
                Map.of("sku", "SHARED-STAGE4", "userId", other.getId())).errorCode());
    }

    private long insertProduct(long userId, String sku) {
        jdbc.update("INSERT INTO products(user_id,sku,name,marketplace,category,price,currency,status) VALUES(?,?,?,?,?,?,?,?)",
                userId, sku, sku, "Amazon US", "Test", 10, "USD", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM products WHERE user_id=? AND sku=?", Long.class, userId, sku);
    }
    private long insertOrder(long userId, String status, java.time.LocalDateTime orderedAt) {
        String number = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO orders(user_id,order_no,marketplace,customer_country,status,total_amount,currency,ordered_at) VALUES(?,?,?,?,?,?,?,?)",
                userId, number, "Amazon US", "US", status, 10, "USD", orderedAt);
        return jdbc.queryForObject("SELECT id FROM orders WHERE user_id=? AND order_no=?", Long.class, userId, number);
    }
    private void insertItem(long orderId, long productId, String sku, int quantity) {
        jdbc.update("INSERT INTO order_items(order_id,product_id,sku,quantity,unit_price,subtotal) VALUES(?,?,?,?,?,?)",
                orderId, productId, sku, quantity, 10, quantity * 10);
    }
    private JsonNode feedback(int index) throws Exception {
        return json.readTree(fake.requests.get(1).get(index).path("content").asText());
    }
    private JsonNode call(String id, String name, String args) {
        var call = json.createObjectNode().put("id", id).put("type", "function");
        call.putObject("function").put("name", name).put("arguments", args);
        return call;
    }
    private void calls(JsonNode... calls) {
        var response = json.createObjectNode().put("role", "assistant");
        response.putNull("content");
        response.set("tool_calls", json.valueToTree(calls));
        fake.replies.add(response);
    }
    private void reply(String answer) {
        fake.replies.add(json.createObjectNode().put("role", "assistant").put("content", answer));
    }
}

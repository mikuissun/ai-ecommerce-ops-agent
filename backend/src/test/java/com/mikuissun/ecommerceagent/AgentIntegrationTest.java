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
        List<JsonNode> tools;
        @Override public JsonNode chat(List<JsonNode> messages, List<JsonNode> tools) {
            requests.add(messages.stream().map(node -> (JsonNode) node.deepCopy()).toList());
            this.tools = tools;
            return replies.removeFirst();
        }
        void reset() { replies.clear(); requests.clear(); tools = null; }
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
        assertEquals(6, fake.tools.size());
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
        assertEquals(6, byName.size());
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
        assertEquals(2, json.readTree(body).size());
        assertFalse(body.contains(token));
        assertFalse(body.contains("userId"));
        assertFalse(body.contains("system"));
        assertFalse(body.contains("stackTrace"));
        assertNull(CurrentUserContext.getUserId());
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

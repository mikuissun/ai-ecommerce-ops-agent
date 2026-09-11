package com.mikuissun.ecommerceagent;

import com.fasterxml.jackson.databind.*;
import com.mikuissun.ecommerceagent.common.*;
import com.mikuissun.ecommerceagent.dto.agent.AgentChatResponse;
import com.mikuissun.ecommerceagent.dto.auth.RegisterRequest;
import com.mikuissun.ecommerceagent.service.*;
import com.mikuissun.ecommerceagent.tool.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:approval_stage6;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AgentIntegrationTest.Config.class)
class ApprovalIntegrationTest {
    @Autowired AgentIntegrationTest.FakeAgentChatModel fake;
    @Autowired ConversationService conversations;
    @Autowired PendingActionService actions;
    @Autowired ProductService products;
    @Autowired ToolExecutor executor;
    @Autowired ToolRegistry registry;
    @Autowired UserService users;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    String sku;

    @BeforeEach void setup() {
        fake.reset();
        CurrentUserContext.setUserId(1L);
        sku = "APPROVAL-" + UUID.randomUUID();
        jdbc.update("INSERT INTO products(user_id,sku,name,marketplace,category,price,currency,status) VALUES(1,?,?,'Amazon US','Test',19.99,'USD','ACTIVE')", sku, sku);
    }
    @AfterEach void cleanup() { CurrentUserContext.clear(); }

    @Test void proposalDoesNotChangePriceAndReturnsApprovalFields() {
        var response = propose();
        assertTrue(response.requiresApproval());
        assertNotNull(response.pendingActionId());
        assertNotNull(response.conversationId());
        assertEquals("PENDING", state(response.pendingActionId()));
        assertEquals(0, price().compareTo(new BigDecimal("19.99")));
        assertEquals(1, fake.requests.size());
        assertEquals("update_product_price", response.toolCalls().get(0).toolName());
        assertTrue(response.answer().contains("尚未修改"));
        assertEquals(response.conversationId(), jdbc.queryForObject("SELECT conversation_id FROM pending_actions WHERE id=?", Long.class, response.pendingActionId()));
        String args = jdbc.queryForObject("SELECT arguments_json FROM pending_actions WHERE id=?", String.class, response.pendingActionId());
        assertFalse(args.contains("userId"));
    }

    @Test void approveUpdatesPriceAndAuditsBeforeAfterAtomically() {
        var proposal = propose();
        var result = actions.approve(proposal.pendingActionId());
        assertEquals("EXECUTED", result.status());
        assertEquals(0, result.result().beforePrice().compareTo(new BigDecimal("19.99")));
        assertEquals(0, result.result().afterPrice().compareTo(new BigDecimal("25.99")));
        assertEquals(0, price().compareTo(new BigDecimal("25.99")));
        var audit = jdbc.queryForMap("SELECT * FROM audit_logs WHERE pending_action_id=?", proposal.pendingActionId());
        assertEquals(sku, audit.get("target"));
        assertEquals("19.99", audit.get("before_value"));
        assertEquals("25.99", audit.get("after_value"));
        assertEquals("SUCCESS", audit.get("status"));
        assertNotNull(jdbc.queryForObject("SELECT executed_at FROM pending_actions WHERE id=?", java.sql.Timestamp.class, proposal.pendingActionId()));
    }

    @Test void rejectDoesNotWriteAndCannotBeApprovedLater() {
        long id = propose().pendingActionId();
        assertEquals("REJECTED", actions.reject(id).status());
        assertEquals(0, price().compareTo(new BigDecimal("19.99")));
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class, () -> actions.approve(id)).getStatus());
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class, () -> actions.reject(id)).getStatus());
        assertEquals(0, auditCount(id));
    }

    @Test void anotherUserCannotApproveOrReject() {
        long id = propose().pendingActionId();
        var other = users.register(new RegisterRequest("approval-" + UUID.randomUUID() + "@example.com", "TestPass123", "Other"));
        CurrentUserContext.setUserId(other.getId());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class, () -> actions.approve(id)).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class, () -> actions.reject(id)).getStatus());
        assertEquals("PENDING", state(id));
        assertEquals(0, auditCount(id));
    }

    @Test void repeatedApprovalCannotExecuteTwice() {
        long id = propose().pendingActionId();
        actions.approve(id);
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class, () -> actions.approve(id)).getStatus());
        assertEquals(1, auditCount(id));
    }

    @Test void invalidPricesNeverCreatePendingActions() {
        for (String price : List.of("0", "-1", "1.001", "100000000000000000", "\"NaN\"", "null", "{}")) {
            int before = totalPending();
            queueCalls(writeCall("invalid", price));
            reply("价格无效");
            var result = conversations.chat(null, "修改价格");
            assertFalse(result.requiresApproval());
            assertFalse(result.toolCalls().get(0).success());
            assertEquals(before, totalPending());
        }
        assertEquals(0, price().compareTo(new BigDecimal("19.99")));
    }

    @Test void deletedProductFailsApprovalAndCreatesFailureAudit() {
        long id = propose().pendingActionId();
        jdbc.update("DELETE FROM products WHERE user_id=1 AND sku=?", sku);
        var response = actions.approve(id);
        assertEquals("FAILED", response.status());
        assertEquals("FAILED", state(id));
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM audit_logs WHERE pending_action_id=?", String.class, id));
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class, () -> actions.approve(id)).getStatus());
    }

    @Test void directExecutorToolAndDebugApiCannotBypassApproval() throws Exception {
        Map<String, Object> args = Map.of("sku", sku, "newPrice", new BigDecimal("25.99"));
        assertEquals("APPROVAL_REQUIRED", executor.execute("update_product_price", args).errorCode());
        assertEquals("APPROVAL_REQUIRED", registry.find("update_product_price").orElseThrow()
                .execute(new ToolArguments(args)).errorCode());
        String token = login("demo@example.com", "password");
        mvc.perform(post("/api/tools/update_product_price/execute").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("arguments", args))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCode").value("APPROVAL_REQUIRED"));
        assertEquals(0, price().compareTo(new BigDecimal("19.99")));
    }

    @Test void injectedUserIdAndUnknownSkuCannotCreateActions() {
        int before = totalPending();
        var call = writeCall("injected", "25.99");
        ((com.fasterxml.jackson.databind.node.ObjectNode) call.path("function")).put("arguments",
                "{\"sku\":\"" + sku + "\",\"newPrice\":25.99,\"userId\":2}");
        queueCalls(call);
        reply("参数不支持");
        assertFalse(conversations.chat(null, "修改").requiresApproval());
        sku = "DOES-NOT-EXIST";
        queueCalls(writeCall("absent", "25.99"));
        reply("商品不存在");
        assertFalse(conversations.chat(null, "修改").requiresApproval());
        assertEquals(before, totalPending());
    }

    @Test void multipleWriteCallsAreRejectedWithoutCreatingBatch() {
        int before = totalPending();
        queueCalls(writeCall("one", "25.99"), writeCall("two", "26.99"));
        reply("请逐个操作");
        var response = conversations.chat(null, "批量修改");
        assertFalse(response.requiresApproval());
        assertEquals(2, response.toolCalls().size());
        assertTrue(response.toolCalls().stream().noneMatch(r -> r.success()));
        assertEquals(before, totalPending());
    }

    @Test void approvalApiRequiresJwtAndIgnoresReplacementArguments() throws Exception {
        long id = propose().pendingActionId();
        CurrentUserContext.clear();
        mvc.perform(post("/api/pending-actions/" + id + "/approve")).andExpect(status().isUnauthorized());
        String token = login("demo@example.com", "password");
        mvc.perform(post("/api/pending-actions/" + id + "/approve").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"newPrice\":999,\"userId\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.result.afterPrice").value(25.99));
        mvc.perform(post("/api/pending-actions/" + id + "/approve").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        assertEquals(0, price().compareTo(new BigDecimal("25.99")));
    }

    @Test void concurrentApprovalsHaveOnlyOneWinner() throws Exception {
        long id = propose().pendingActionId();
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> attempt = () -> {
                CurrentUserContext.setUserId(1L);
                try { return actions.approve(id).status(); }
                catch (BusinessException ex) { return ex.getStatus().name(); }
                finally { CurrentUserContext.clear(); }
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertEquals(Set.of("EXECUTED", "CONFLICT"), Set.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
            assertEquals(1, auditCount(id));
        } finally { pool.shutdownNow(); }
    }

    @Test void auditInsertionFailureRollsBackPriceAndApprovalState() {
        long id = propose().pendingActionId();
        // Force unique-key failure at the last step of approve.
        jdbc.update("INSERT INTO audit_logs(pending_action_id,user_id,action_type,target,status) VALUES(?,1,'test',?,'FAILED')", id, sku);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> actions.approve(id));
        assertEquals(0, price().compareTo(new BigDecimal("19.99")));
        assertEquals("PENDING", state(id));
        assertEquals(1, auditCount(id));
    }

    @Test void chatApiReturnsProposalAndRejectApiKeepsPrice() throws Exception {
        queueCalls(writeCall("request", "25.99"));
        String token = login("demo@example.com", "password");
        String body = mvc.perform(post("/api/agent/chat").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"改价\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requiresApproval").value(true))
                .andExpect(jsonPath("$.pendingActionId").isNumber()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).path("pendingActionId").asLong();
        mvc.perform(post("/api/pending-actions/" + id + "/reject").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertEquals(0, price().compareTo(new BigDecimal("19.99")));
        assertFalse(body.contains("userId"));
    }

    private AgentChatResponse propose() {
        queueCalls(writeCall("price", "25.99"));
        return conversations.chat(null, "把 " + sku + " 的价格改成 25.99");
    }
    private JsonNode writeCall(String id, String price) {
        var call = json.createObjectNode().put("id", id).put("type", "function");
        call.putObject("function").put("name", "update_product_price")
                .put("arguments", "{\"sku\":\"" + sku + "\",\"newPrice\":" + price + "}");
        return call;
    }
    private void queueCalls(JsonNode... calls) {
        var response = json.createObjectNode().put("role", "assistant");
        response.set("tool_calls", json.valueToTree(calls));
        fake.replies.add(response);
    }
    private void reply(String content) {
        fake.replies.add(json.createObjectNode().put("role", "assistant").put("content", content));
    }
    private BigDecimal price() { return jdbc.queryForObject("SELECT price FROM products WHERE user_id=1 AND sku=?", BigDecimal.class, sku); }
    private String state(long id) { return jdbc.queryForObject("SELECT status FROM pending_actions WHERE id=?", String.class, id); }
    private int auditCount(long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE pending_action_id=?", Integer.class, id); }
    private int totalPending() { return jdbc.queryForObject("SELECT COUNT(*) FROM pending_actions", Integer.class); }
    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("accessToken").asText();
    }
}

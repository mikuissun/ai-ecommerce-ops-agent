package com.mikuissun.ecommerceagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.ecommerceagent.common.*;
import com.mikuissun.ecommerceagent.dto.auth.RegisterRequest;
import com.mikuissun.ecommerceagent.mapper.ConversationMapper;
import com.mikuissun.ecommerceagent.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:conversation_stage5;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "agent.max-history-messages=3"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AgentIntegrationTest.Config.class)
class ConversationIntegrationTest {
    @Autowired AgentIntegrationTest.FakeAgentChatModel fake;
    @Autowired ConversationService conversations;
    @Autowired ConversationMapper mapper;
    @Autowired AgentService agent;
    @Autowired UserService users;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired PendingActionService pendingActions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach void setup() { fake.reset(); CurrentUserContext.setUserId(1L); }
    @AfterEach void cleanup() { CurrentUserContext.clear(); }

    @Test void firstChatCreatesConversationAndPersistsOrderedMessages() {
        reply("SKU-A001 的库存已查询。");
        var result = conversations.chat(null, "SKU-A001 库存怎么样？");
        assertNotNull(result.conversationId());
        var stored = conversations.messages(result.conversationId(), 100, 0);
        assertEquals(List.of("USER", "ASSISTANT"), stored.stream().map(m -> m.role()).toList());
        assertEquals("SKU-A001 库存怎么样？", stored.get(0).content());
        assertEquals(result.answer(), stored.get(1).content());
        var row = mapper.findOwned(result.conversationId(), 1L);
        assertEquals("SKU-A001 库存怎么样？", row.getTitle());
        assertNotNull(row.getCreatedAt());
        assertNotNull(row.getUpdatedAt());
        assertEquals(2, fake.requests.get(0).size());
    }

    @Test void continuationPassesPriorUserAndAnswerBeforeCurrentMessage() {
        reply("SKU-A001 当前库存已查询。");
        var first = conversations.chat(null, "SKU-A001 库存怎么样？");
        reply("它指 SKU-A001。");
        var second = conversations.chat(first.conversationId(), "那它呢？");
        assertEquals(first.conversationId(), second.conversationId());
        var messages = fake.requests.get(1);
        assertEquals(List.of("system", "user", "assistant", "user"),
                messages.stream().map(m -> m.path("role").asText()).toList());
        assertEquals("SKU-A001 库存怎么样？", messages.get(1).path("content").asText());
        assertEquals(first.answer(), messages.get(2).path("content").asText());
        assertEquals("那它呢？", messages.get(3).path("content").asText());
        assertEquals(4, conversations.messages(first.conversationId(), 100, 0).size());
    }

    @Test void boundedHistoryKeepsNewestMessagesChronologicallyWithoutDeletingOlderData() {
        reply("回答一");
        var first = conversations.chat(null, "问题一");
        reply("回答二");
        conversations.chat(first.conversationId(), "问题二");
        reply("回答三");
        conversations.chat(first.conversationId(), "问题三");
        var history = fake.requests.get(2);
        assertEquals(4, history.size()); // system + configured 3 messages, including current user
        assertEquals(List.of("问题二", "回答二", "问题三"),
                history.subList(1, 4).stream().map(m -> m.path("content").asText()).toList());
        var stored = conversations.messages(first.conversationId(), 100, 0);
        assertEquals(6, stored.size());
        assertEquals("问题一", stored.get(0).content());
        assertEquals(stored.subList(2, 4), conversations.messages(first.conversationId(), 2, 2));
    }

    @Test void multiTurnInventoryThenSalesUsesHistoryAndRetainsInternalToolMessagesOnlyInLoop() throws Exception {
        call("stock", "get_inventory", "{\"sku\":\"SKU-A001\"}");
        reply("SKU-A001 库存已查询。");
        var first = conversations.chat(null, "SKU-A001 库存怎么样？");
        call("sales", "get_sales_summary", "{\"sku\":\"SKU-A001\",\"days\":7}");
        reply("SKU-A001 最近7天销量已查询。");
        var second = conversations.chat(first.conversationId(), "那它最近7天卖得怎么样？");
        var secondTurn = fake.requests.get(2);
        assertTrue(secondTurn.get(1).path("content").asText().contains("SKU-A001"));
        assertTrue(secondTurn.get(2).path("content").asText().contains("SKU-A001"));
        var afterTool = fake.requests.get(3);
        assertEquals("sales", afterTool.get(5).path("tool_call_id").asText());
        var result = json.readTree(afterTool.get(5).path("content").asText());
        assertEquals("SKU-A001", result.path("data").path("sku").asText());
        assertTrue(second.toolCalls().get(0).success());
        var persisted = conversations.messages(first.conversationId(), 100, 0);
        assertEquals(4, persisted.size());
        assertTrue(persisted.stream().noneMatch(m -> m.role().equals("TOOL")));
        assertEquals(1, second.toolCalls().get(0).iteration());
    }

    @Test void otherUserCannotContinueReadOrListConversation() {
        reply("用户一私有回答");
        var first = conversations.chat(null, "用户一私有问题");
        var other = users.register(new RegisterRequest("memory-" + UUID.randomUUID() + "@example.com", "TestPass123", "Other"));
        CurrentUserContext.setUserId(other.getId());
        var failure = assertThrows(BusinessException.class,
                () -> conversations.chat(first.conversationId(), "读取用户一"));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, failure.getStatus());
        assertThrows(BusinessException.class, () -> conversations.messages(first.conversationId(), 100, 0));
        assertTrue(conversations.list(100, 0).isEmpty());
        assertEquals(1, fake.requests.size());
        CurrentUserContext.setUserId(1L);
        assertEquals(2, conversations.messages(first.conversationId(), 100, 0).size());
    }

    @Test void differentConversationsDoNotShareHistory() {
        reply("秘密甲");
        var first = conversations.chat(null, "问题甲");
        reply("回答乙");
        var second = conversations.chat(null, "问题乙");
        assertNotEquals(first.conversationId(), second.conversationId());
        assertEquals(2, fake.requests.get(1).size());
        assertFalse(fake.requests.get(1).toString().contains("秘密甲"));
    }

    @Test void modelFailureRollsBackNewConversationAndFailedContinuation() {
        int before = conversations.list(100, 0).size();
        assertThrows(NoSuchElementException.class, () -> conversations.chat(null, "模型失败"));
        assertEquals(before, conversations.list(100, 0).size());
        reply("成功回答");
        var first = conversations.chat(null, "成功问题");
        var original = conversations.messages(first.conversationId(), 100, 0);
        assertThrows(NoSuchElementException.class, () -> conversations.chat(first.conversationId(), "失败续聊"));
        assertEquals(original, conversations.messages(first.conversationId(), 100, 0));
    }

    @Test void titleIsTruncatedAndPagingAndHistoryConfigurationAreValidated() {
        reply("完成");
        var result = conversations.chat(null, "a".repeat(150));
        assertEquals(100, mapper.findOwned(result.conversationId(), 1L).getTitle().length());
        assertThrows(BusinessException.class, () -> conversations.list(101, 0));
        assertThrows(BusinessException.class, () -> conversations.messages(result.conversationId(), 10, -1));
        assertThrows(IllegalArgumentException.class, () -> new ConversationService(mapper, agent, 0, transactionManager));
        assertThrows(IllegalArgumentException.class, () -> new ConversationService(mapper, agent, 101, transactionManager));
        assertThrows(BusinessException.class, () -> conversations.chat(null, " "));
    }

    @Test void apiCreatesContinuesListsAndReturnsMessagesWithoutUserIdentity() throws Exception {
        CurrentUserContext.clear();
        mvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/conversations/1/messages")).andExpect(status().isUnauthorized());
        String token = login("demo@example.com", "password");
        reply("第一轮");
        String body = mvc.perform(post("/api/agent/chat").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"API问题\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conversationId").isNumber())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).path("conversationId").asLong();
        reply("第二轮");
        mvc.perform(post("/api/agent/chat").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":" + id + ",\"message\":\"继续\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conversationId").value(id));
        mvc.perform(get("/api/conversations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        String history = mvc.perform(get("/api/conversations/" + id + "/messages")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(4)))
                .andReturn().getResponse().getContentAsString();
        assertFalse(history.contains("userId"));
        assertFalse(history.contains(token));
        assertFalse(history.contains("system"));
    }

    @Test void apiRejectsForeignConversationForBothContinuationAndMessages() throws Exception {
        reply("A的回答");
        var first = conversations.chat(null, "A的会话");
        var other = users.register(new RegisterRequest("memory-api-" + UUID.randomUUID() + "@example.com", "TestPass123", "Other"));
        CurrentUserContext.clear();
        String token = login(other.getEmail(), "TestPass123");
        mvc.perform(post("/api/agent/chat").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":" + first.conversationId() + ",\"message\":\"读取\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/conversations/" + first.conversationId() + "/messages")
                .header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        mvc.perform(post("/api/agent/chat").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"conversationId\":0,\"message\":\"读取\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(1, fake.requests.size());
    }

    @Test void modelAndToolLoopRunWithoutTransactionOrBoundDatabaseConnection() {
        var checkedCalls = new java.util.concurrent.atomic.AtomicInteger();
        fake.beforeChat = () -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(dataSource));
            checkedCalls.incrementAndGet();
        };
        call("stock", "get_inventory", "{\"sku\":\"SKU-A001\"}");
        reply("库存已查询");
        var first = conversations.chat(null, "SKU-A001 库存？");
        call("sales", "get_sales_summary", "{\"sku\":\"SKU-A001\"}");
        reply("销量已查询");
        conversations.chat(first.conversationId(), "那它的销量？");
        assertEquals(4, checkedCalls.get()); // before and after Tools, both new and existing conversations
        assertEquals(4, conversations.messages(first.conversationId(), 100, 0).size());
    }

    @Test void deleteApiRequiresJwtAndRemovesOwnConversationAndMessages() throws Exception {
        reply("删除测试回答");
        long id = conversations.chat(null, "删除测试").conversationId();
        CurrentUserContext.clear();
        mvc.perform(delete("/api/conversations/" + id)).andExpect(status().isUnauthorized());
        String token = login("demo@example.com", "password");
        mvc.perform(delete("/api/conversations/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(get("/api/conversations/" + id + "/messages").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/conversations/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages WHERE conversation_id=?", Integer.class, id));
        assertNull(mapper.findOwned(id, 1L));
    }

    @Test void deleteCannotTouchOtherUsersConversationOrRelatedData() throws Exception {
        reply("私有回答");
        long id = conversations.chat(null, "私有会话").conversationId();
        var action = pendingActions.create(id, "update_product_price", Map.of("sku", "SKU-A001", "newPrice", 25.99));
        var other = users.register(new RegisterRequest("delete-" + UUID.randomUUID() + "@example.com", "TestPass123", "Other"));
        CurrentUserContext.clear();
        String token = login(other.getEmail(), "TestPass123");
        mvc.perform(delete("/api/conversations/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        assertNotNull(mapper.findOwned(id, 1L));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages WHERE conversation_id=?", Integer.class, id));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM pending_actions WHERE id=?", String.class, action.getId()));
        assertEquals(id, jdbc.queryForObject("SELECT conversation_id FROM pending_actions WHERE id=?", Long.class, action.getId()));
    }

    @Test void deleteRejectsPendingActionsAndPreservesCompletedActionsAndAudits() {
        reply("带审批的会话");
        long id = conversations.chat(null, "审批删除测试").conversationId();
        var pending = pendingActions.create(id, "update_product_price", Map.of("sku", "SKU-A001", "newPrice", 25.99));
        var executed = pendingActions.create(id, "update_product_price", Map.of("sku", "SKU-A001", "newPrice", 26.99));
        assertEquals("EXECUTED", pendingActions.approve(executed.getId()).status());
        var audit = jdbc.queryForMap("SELECT * FROM audit_logs WHERE pending_action_id=?", executed.getId());
        reply("保留的会话");
        long kept = conversations.chat(null, "不删除这个会话").conversationId();
        var keptAction = pendingActions.create(kept, "update_product_price", Map.of("sku", "SKU-A001", "newPrice", 27.99));

        conversations.delete(id);

        assertNull(mapper.findOwned(id, 1L));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages WHERE conversation_id=?", Integer.class, id));
        for (long actionId : List.of(pending.getId(), executed.getId())) {
            assertNull(jdbc.queryForObject("SELECT conversation_id FROM pending_actions WHERE id=?", Long.class, actionId));
        }
        assertEquals("REJECTED", jdbc.queryForObject("SELECT status FROM pending_actions WHERE id=?", String.class, pending.getId()));
        assertEquals("EXECUTED", jdbc.queryForObject("SELECT status FROM pending_actions WHERE id=?", String.class, executed.getId()));
        assertEquals(audit, jdbc.queryForMap("SELECT * FROM audit_logs WHERE pending_action_id=?", executed.getId()));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT,
                assertThrows(BusinessException.class, () -> pendingActions.approve(pending.getId())).getStatus());
        assertEquals(2, conversations.messages(kept, 100, 0).size());
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM pending_actions WHERE id=?", String.class, keptAction.getId()));
        assertEquals(kept, jdbc.queryForObject("SELECT conversation_id FROM pending_actions WHERE id=?", Long.class, keptAction.getId()));
    }

    private void reply(String answer) {
        fake.replies.add(json.createObjectNode().put("role", "assistant").put("content", answer));
    }
    private void call(String id, String name, String args) {
        var response = json.createObjectNode().put("role", "assistant");
        response.putArray("tool_calls").addObject().put("id", id).put("type", "function")
                .putObject("function").put("name", name).put("arguments", args);
        fake.replies.add(response);
    }
    private String login(String email, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("accessToken").asText();
    }
}

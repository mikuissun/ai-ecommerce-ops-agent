package com.mikuissun.ecommerceagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.ecommerceagent.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class QwenChatModelTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void sendsCompatibleRequestAndParsesToolCalls() {
        var builder = RestClient.builder().baseUrl("https://test.invalid/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://test.invalid/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer fake-test-key"))
                .andExpect(jsonPath("$.model").value("qwen-plus"))
                .andExpect(jsonPath("$.messages[0].content").value("库存"))
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andExpect(jsonPath("$.stream").value(false))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
                        {"id":"abc","type":"function","function":{"name":"get_inventory","arguments":"{}"}}]}}]}
                        """, MediaType.APPLICATION_JSON));
        var model = new QwenChatModel(builder.build(), "qwen-plus", () -> "fake-test-key");
        var result = model.chat(List.of(json.createObjectNode().put("role", "user").put("content", "库存")),
                List.of(json.createObjectNode().put("type", "function")));
        assertEquals("abc", result.path("tool_calls").get(0).path("id").asText());
        server.verify();
    }

    @Test void missingKeyFailsWithoutSendingHttpRequest() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var model = new QwenChatModel(builder.build(), "qwen-plus", () -> null);
        var error = assertThrows(BusinessException.class, () -> model.chat(List.of(), List.of()));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        server.verify();
    }

    @Test void providerErrorIsSanitized() {
        var builder = RestClient.builder().baseUrl("https://test.invalid");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://test.invalid/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("sensitive-provider-body"));
        var model = new QwenChatModel(builder.build(), "qwen-plus", () -> "fake-test-key");
        var error = assertThrows(BusinessException.class, () -> model.chat(List.of(), List.of()));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
        assertFalse(error.getMessage().contains("sensitive"));
        server.verify();
    }

    @Test void malformedProviderResponseIsSanitized() {
        var builder = RestClient.builder().baseUrl("https://test.invalid");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://test.invalid/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        var model = new QwenChatModel(builder.build(), "qwen-plus", () -> "fake-test-key");
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(BusinessException.class,
                () -> model.chat(List.of(), List.of())).getStatus());
        server.verify();
    }
}

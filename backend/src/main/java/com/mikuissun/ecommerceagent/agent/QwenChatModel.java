package com.mikuissun.ecommerceagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.mikuissun.ecommerceagent.common.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class QwenChatModel implements AgentChatModel {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QwenChatModel.class);
    private final RestClient http;
    private final String model;
    private final Supplier<String> apiKey;

    @Autowired
    public QwenChatModel(@Value("${agent.model:qwen-plus}") String model) {
        this(client(), model, () -> System.getenv("DASHSCOPE_API_KEY"));
    }

    // Package-private transport seam for local HTTP contract tests.
    QwenChatModel(RestClient http, String model, Supplier<String> apiKey) {
        this.http = http;
        this.model = model;
        this.apiKey = apiKey;
    }

    private static RestClient client() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .requestFactory(factory).build();
    }

    @Override
    public JsonNode chat(List<JsonNode> messages, List<JsonNode> tools) {
        String key = apiKey.get();
        if (key == null || key.isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "未配置 DASHSCOPE_API_KEY 环境变量");
        }
        try {
            JsonNode response = http.post().uri("/chat/completions")
                    .headers(headers -> headers.setBearerAuth(key))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", model, "messages", messages, "tools", tools,
                            "tool_choice", "auto", "stream", false, "enable_thinking", false,
                            "parallel_tool_calls", true))
                    .retrieve().body(JsonNode.class);
            JsonNode message = response == null ? null : response.path("choices").path(0).path("message");
            if (message == null || !message.isObject() || !"assistant".equals(message.path("role").asText())) {
                throw new IllegalStateException("Invalid model response");
            }
            return message;
        } catch (Exception ex) {
            log.warn("Qwen request failed: type={}, status={}, causeType={}", ex.getClass().getSimpleName(),
                    ex instanceof org.springframework.web.client.RestClientResponseException responseError
                            ? responseError.getStatusCode().value() : "transport",
                    ex.getCause() == null ? "none" : ex.getCause().getClass().getSimpleName());
            // Never expose provider body, request headers, credentials or stack traces.
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "模型服务暂时不可用，请稍后重试");
        }
    }
}

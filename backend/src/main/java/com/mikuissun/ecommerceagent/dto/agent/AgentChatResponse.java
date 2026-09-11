package com.mikuissun.ecommerceagent.dto.agent;

import java.util.List;
import java.util.Map;

public record AgentChatResponse(Long conversationId, String answer, List<ToolCallRecord> toolCalls) {
    public AgentChatResponse(String answer, List<ToolCallRecord> toolCalls) { this(null, answer, toolCalls); }
    public AgentChatResponse { toolCalls = List.copyOf(toolCalls); }
    public record ToolCallRecord(int iteration, String toolName, Map<String, Object> arguments, boolean success) {
        public ToolCallRecord { arguments = Map.copyOf(arguments); }
    }
}

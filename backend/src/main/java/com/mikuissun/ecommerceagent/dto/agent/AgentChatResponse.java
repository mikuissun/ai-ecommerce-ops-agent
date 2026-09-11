package com.mikuissun.ecommerceagent.dto.agent;

import java.util.List;
import java.util.Map;

public record AgentChatResponse(Long conversationId, String answer, List<ToolCallRecord> toolCalls,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Long pendingActionId,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT) boolean requiresApproval) {
    public AgentChatResponse(String answer, List<ToolCallRecord> toolCalls) { this(null, answer, toolCalls, null, false); }
    public AgentChatResponse(Long conversationId, String answer, List<ToolCallRecord> toolCalls) {
        this(conversationId, answer, toolCalls, null, false);
    }
    public AgentChatResponse { toolCalls = List.copyOf(toolCalls); }
    public record ToolCallRecord(int iteration, String toolName, Map<String, Object> arguments, boolean success) {
        public ToolCallRecord { arguments = Map.copyOf(arguments); }
    }
}

package com.mikuissun.ecommerceagent.dto.agent;

import java.util.List;

public record AgentChatResponse(String answer, List<ToolCallRecord> toolCalls) {
    public AgentChatResponse { toolCalls = List.copyOf(toolCalls); }
    public record ToolCallRecord(String toolName, boolean success) {}
}

package com.mikuissun.ecommerceagent.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentChatRequest(
        @jakarta.validation.constraints.Positive(message = "conversationId 必须为正数") Long conversationId,
        @NotBlank(message = "message 不能为空")
        @Size(max = 10000, message = "message 不能超过 10000 字符") String message) {}

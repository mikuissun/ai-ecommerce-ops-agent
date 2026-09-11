package com.mikuissun.ecommerceagent.dto.conversation;

import java.time.LocalDateTime;
public record ConversationMessageResponse(Long id, String role, String content, LocalDateTime createdAt) {}

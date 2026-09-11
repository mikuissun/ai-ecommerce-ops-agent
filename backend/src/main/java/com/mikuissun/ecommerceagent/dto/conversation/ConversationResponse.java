package com.mikuissun.ecommerceagent.dto.conversation;

import java.time.LocalDateTime;
public record ConversationResponse(Long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {}

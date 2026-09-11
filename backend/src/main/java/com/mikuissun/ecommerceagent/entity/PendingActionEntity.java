package com.mikuissun.ecommerceagent.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PendingActionEntity {
    private Long id;
    private Long userId;
    private Long conversationId;
    private String toolName;
    private String argumentsJson;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime executedAt;
}

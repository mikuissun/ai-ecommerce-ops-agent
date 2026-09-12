package com.mikuissun.ecommerceagent.dto.agent;

import java.util.Map;

public record PendingActionResponse(long pendingActionId, String toolName,
                                    Map<String, Object> arguments, String status) {}

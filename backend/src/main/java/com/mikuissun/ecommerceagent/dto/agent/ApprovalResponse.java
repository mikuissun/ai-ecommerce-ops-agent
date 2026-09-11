package com.mikuissun.ecommerceagent.dto.agent;

import com.mikuissun.ecommerceagent.dto.product.PriceChangeResponse;
public record ApprovalResponse(long pendingActionId, String status, PriceChangeResponse result, String errorMessage) {}

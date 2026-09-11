package com.mikuissun.ecommerceagent.dto.analytics;

import java.math.BigDecimal;
import java.util.List;

public record SalesSummaryResponse(long orderCount, BigDecimal revenue, long completedOrderCount,
                                   long refundCount, List<TopSellingProductResponse> topSellingProducts) {}


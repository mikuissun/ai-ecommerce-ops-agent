package com.mikuissun.ecommerceagent.dto.dashboard;

import java.math.BigDecimal;

public record DashboardSummaryResponse(long productCount, long lowStockCount, long orderCount,
                                       long recent7DaysOrderCount, BigDecimal recent7DaysRevenue,
                                       long refundCount, long completedOrderCount) {}

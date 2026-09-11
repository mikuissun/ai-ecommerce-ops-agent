package com.mikuissun.ecommerceagent.dto.analytics;

import java.math.BigDecimal;

public record ProductSalesResponse(String sku, int days, long unitsSold,
                                   BigDecimal revenue, long orderCount, String currency) {}

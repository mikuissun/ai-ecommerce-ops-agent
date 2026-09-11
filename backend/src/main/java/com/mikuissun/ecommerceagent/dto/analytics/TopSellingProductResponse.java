package com.mikuissun.ecommerceagent.dto.analytics;

import java.math.BigDecimal;

public record TopSellingProductResponse(String sku, String productName, long unitsSold, BigDecimal revenue) {}


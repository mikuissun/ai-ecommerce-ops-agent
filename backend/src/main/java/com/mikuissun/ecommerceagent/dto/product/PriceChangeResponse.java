package com.mikuissun.ecommerceagent.dto.product;

import java.math.BigDecimal;
public record PriceChangeResponse(String sku, BigDecimal beforePrice, BigDecimal afterPrice) {}

package com.mikuissun.ecommerceagent.mapper;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TopSellingProductRow {
    private String sku;
    private String productName;
    private Long unitsSold;
    private BigDecimal revenue;
}


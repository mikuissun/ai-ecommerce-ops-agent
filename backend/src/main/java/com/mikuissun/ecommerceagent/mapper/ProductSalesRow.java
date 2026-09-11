package com.mikuissun.ecommerceagent.mapper;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductSalesRow {
    private long unitsSold;
    private BigDecimal revenue;
    private long orderCount;
}

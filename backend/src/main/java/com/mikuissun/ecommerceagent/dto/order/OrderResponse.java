package com.mikuissun.ecommerceagent.dto.order;

import com.mikuissun.ecommerceagent.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(Long id, String orderNo, String marketplace, String customerCountry, String status,
                            BigDecimal totalAmount, String currency, LocalDateTime orderedAt, LocalDateTime createdAt) {
    public static OrderResponse from(OrderEntity o) {
        return new OrderResponse(o.getId(), o.getOrderNo(), o.getMarketplace(), o.getCustomerCountry(), o.getStatus(),
                o.getTotalAmount(), o.getCurrency(), o.getOrderedAt(), o.getCreatedAt());
    }
}

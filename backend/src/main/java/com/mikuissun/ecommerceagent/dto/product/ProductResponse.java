package com.mikuissun.ecommerceagent.dto.product;

import com.mikuissun.ecommerceagent.entity.ProductEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(Long id, String sku, String name, String marketplace, String category,
                              BigDecimal price, String currency, String status,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static ProductResponse from(ProductEntity p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getMarketplace(), p.getCategory(),
                p.getPrice(), p.getCurrency(), p.getStatus(), p.getCreatedAt(), p.getUpdatedAt());
    }
}

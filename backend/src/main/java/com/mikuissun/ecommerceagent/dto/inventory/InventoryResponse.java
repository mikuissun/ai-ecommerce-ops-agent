package com.mikuissun.ecommerceagent.dto.inventory;

import com.mikuissun.ecommerceagent.entity.InventoryEntity;

import java.time.LocalDateTime;

public record InventoryResponse(Long productId, Integer availableQuantity, Integer reservedQuantity,
                                Integer reorderThreshold, boolean lowStock, LocalDateTime updatedAt) {
    public static InventoryResponse from(InventoryEntity i) {
        return new InventoryResponse(i.getProductId(), i.getAvailableQuantity(), i.getReservedQuantity(),
                i.getReorderThreshold(), i.getAvailableQuantity() <= i.getReorderThreshold(), i.getUpdatedAt());
    }
}

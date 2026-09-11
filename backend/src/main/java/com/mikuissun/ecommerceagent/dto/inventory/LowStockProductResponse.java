package com.mikuissun.ecommerceagent.dto.inventory;

public record LowStockProductResponse(Long productId, String sku, String productName, String marketplace,
                                      Integer availableQuantity, Integer reservedQuantity,
                                      Integer reorderThreshold, boolean lowStock) {}


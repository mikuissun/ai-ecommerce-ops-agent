package com.mikuissun.ecommerceagent.dto.inventory;

public record InventoryToolResponse(String sku, String productName, Integer availableQuantity,
                                    Integer reservedQuantity, Integer reorderThreshold, boolean lowStock) {}


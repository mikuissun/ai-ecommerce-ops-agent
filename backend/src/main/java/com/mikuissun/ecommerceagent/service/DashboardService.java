package com.mikuissun.ecommerceagent.service;

import com.mikuissun.ecommerceagent.dto.dashboard.DashboardSummaryResponse;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final OrderService orderService;

    public DashboardService(ProductService productService, InventoryService inventoryService, OrderService orderService) {
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
    }

    public DashboardSummaryResponse summary(long userId) {
        return new DashboardSummaryResponse(
                productService.count(userId),
                inventoryService.lowStockCount(userId),
                orderService.count(userId),
                orderService.recent7DaysOrderCount(userId),
                orderService.recent7DaysRevenue(userId),
                orderService.countByStatus(userId, "REFUNDED"),
                orderService.countByStatus(userId, "COMPLETED")
        );
    }
}

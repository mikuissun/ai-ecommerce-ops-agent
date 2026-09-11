package com.mikuissun.ecommerceagent.controller;

import com.mikuissun.ecommerceagent.common.ApiResponse;
import com.mikuissun.ecommerceagent.common.CurrentUserContext;
import com.mikuissun.ecommerceagent.dto.inventory.InventoryResponse;
import com.mikuissun.ecommerceagent.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) { this.inventoryService = inventoryService; }

    @GetMapping
    public ApiResponse<List<InventoryResponse>> list(@RequestParam(defaultValue = "false") boolean lowStock) {
        return ApiResponse.ok(inventoryService.list(CurrentUserContext.requireUserId(), lowStock));
    }

    @GetMapping("/{productId}")
    public ApiResponse<InventoryResponse> get(@PathVariable long productId) {
        return ApiResponse.ok(inventoryService.getByProduct(CurrentUserContext.requireUserId(), productId));
    }
}

package com.mikuissun.ecommerceagent.tool;

import com.mikuissun.ecommerceagent.common.CurrentUserContext;
import com.mikuissun.ecommerceagent.dto.inventory.InventoryResponse;
import com.mikuissun.ecommerceagent.dto.inventory.InventoryToolResponse;
import com.mikuissun.ecommerceagent.dto.product.ProductResponse;
import com.mikuissun.ecommerceagent.service.InventoryService;
import com.mikuissun.ecommerceagent.service.ProductService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetInventoryTool implements Tool {
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "get_inventory", "查询当前用户某个 SKU 的库存", List.of(
            ToolParameterSchema.requiredString("sku", "商品 SKU")
    ));

    private final ProductService productService;
    private final InventoryService inventoryService;

    public GetInventoryTool(ProductService productService, InventoryService inventoryService) {
        this.productService = productService;
        this.inventoryService = inventoryService;
    }

    @Override public ToolDefinition definition() { return DEFINITION; }

    @Override public ToolResult execute(ToolArguments arguments) {
        long userId = CurrentUserContext.requireUserId();
        ProductResponse product = productService.getBySku(userId, arguments.getString("sku"));
        InventoryResponse inventory = inventoryService.getByProduct(userId, product.id());
        return ToolResult.success(new InventoryToolResponse(product.sku(), product.name(),
                inventory.availableQuantity(), inventory.reservedQuantity(), inventory.reorderThreshold(), inventory.lowStock()));
    }
}


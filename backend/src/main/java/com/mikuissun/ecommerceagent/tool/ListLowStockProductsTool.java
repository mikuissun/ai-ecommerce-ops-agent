package com.mikuissun.ecommerceagent.tool;

import com.mikuissun.ecommerceagent.common.CurrentUserContext;
import com.mikuissun.ecommerceagent.service.InventoryService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ListLowStockProductsTool implements Tool {
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "list_low_stock_products", "列出当前用户库存不足商品，最多返回 50 条", List.of(
            ToolParameterSchema.optionalString("marketplace", "销售平台")
    ));

    private final InventoryService inventoryService;

    public ListLowStockProductsTool(InventoryService inventoryService) { this.inventoryService = inventoryService; }

    @Override public ToolDefinition definition() { return DEFINITION; }

    @Override public ToolResult execute(ToolArguments arguments) {
        return ToolResult.success(inventoryService.listLowStock(CurrentUserContext.requireUserId(),
                arguments.getString("marketplace"), 50));
    }
}


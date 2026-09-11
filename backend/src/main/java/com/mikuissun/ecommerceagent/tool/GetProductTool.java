package com.mikuissun.ecommerceagent.tool;

import com.mikuissun.ecommerceagent.common.CurrentUserContext;
import com.mikuissun.ecommerceagent.service.ProductService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetProductTool implements Tool {
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "get_product", "根据 SKU 查询当前用户商品", List.of(
            ToolParameterSchema.requiredString("sku", "商品 SKU")
    ));

    private final ProductService productService;

    public GetProductTool(ProductService productService) { this.productService = productService; }

    @Override public ToolDefinition definition() { return DEFINITION; }

    @Override public ToolResult execute(ToolArguments arguments) {
        return ToolResult.success(productService.getBySku(CurrentUserContext.requireUserId(), arguments.getString("sku")));
    }
}


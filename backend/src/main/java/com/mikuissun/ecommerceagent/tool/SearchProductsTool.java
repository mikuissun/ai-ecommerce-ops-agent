package com.mikuissun.ecommerceagent.tool;

import com.mikuissun.ecommerceagent.common.CurrentUserContext;
import com.mikuissun.ecommerceagent.service.ProductService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchProductsTool implements Tool {
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "search_products", "搜索当前用户商品，最多返回 50 条", List.of(
            ToolParameterSchema.optionalString("keyword", "SKU 或商品名称关键词"),
            ToolParameterSchema.optionalString("marketplace", "销售平台"),
            ToolParameterSchema.optionalEnum("status", "商品状态", List.of("ACTIVE", "INACTIVE"))
    ));

    private final ProductService productService;

    public SearchProductsTool(ProductService productService) { this.productService = productService; }

    @Override public ToolDefinition definition() { return DEFINITION; }

    @Override public ToolResult execute(ToolArguments arguments) {
        return ToolResult.success(productService.list(CurrentUserContext.requireUserId(),
                arguments.getString("keyword"), arguments.getString("marketplace"), arguments.getString("status"), 50));
    }
}


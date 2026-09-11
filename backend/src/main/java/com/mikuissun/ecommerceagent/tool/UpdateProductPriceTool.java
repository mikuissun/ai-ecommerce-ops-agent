package com.mikuissun.ecommerceagent.tool;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class UpdateProductPriceTool implements Tool {
    private static final ToolDefinition DEFINITION = new ToolDefinition("update_product_price",
            "提议修改单个商品价格，需要用户通过审批接口确认后才会执行；本次调用不会修改价格。",
            List.of(ToolParameterSchema.requiredString("sku", "商品 SKU"),
                    new ToolParameterSchema("newPrice", ToolParameterType.NUMBER,
                            "新价格，必须大于 0，最多两位小数", true, List.of(), 0, null)));

    @Override public ToolDefinition definition() { return DEFINITION; }
    @Override public boolean requiresApproval() { return true; }
    @Override public ToolResult execute(ToolArguments arguments) {
        // Even direct calls cannot write; the only write entry point is the approval service.
        return ToolResult.failure("APPROVAL_REQUIRED", "价格修改必须通过 Pending Action 审批接口");
    }
}

package com.mikuissun.ecommerceagent.tool;

import com.mikuissun.ecommerceagent.common.CurrentUserContext;
import com.mikuissun.ecommerceagent.service.SalesSummaryService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetSalesSummaryTool implements Tool {
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "get_sales_summary", "查询指定天数内的销售汇总，默认最近 7 天", List.of(
            ToolParameterSchema.optionalInteger("days", "统计天数，范围 1-90", 1, 90)
    ));

    private final SalesSummaryService salesSummaryService;

    public GetSalesSummaryTool(SalesSummaryService salesSummaryService) {
        this.salesSummaryService = salesSummaryService;
    }

    @Override public ToolDefinition definition() { return DEFINITION; }

    @Override public ToolResult execute(ToolArguments arguments) {
        Integer days = arguments.contains("days") ? arguments.getInteger("days") : 7;
        return ToolResult.success(salesSummaryService.summary(CurrentUserContext.requireUserId(), days));
    }
}


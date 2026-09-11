package com.mikuissun.ecommerceagent.tool;

import com.mikuissun.ecommerceagent.common.CurrentUserContext;
import com.mikuissun.ecommerceagent.service.SalesSummaryService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetSalesSummaryTool implements Tool {
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "get_sales_summary", "查询销售数据，默认最近 7 天。不传 sku 返回全店汇总及销量前 10 商品（未上榜不代表零销量）；传 sku 返回该商品准确的 unitsSold、revenue、orderCount、currency 和 days。销量仅计 PAID/SHIPPED/COMPLETED，排除退款和取消订单。", List.of(
            ToolParameterSchema.optionalString("sku", "可选 SKU；分析指定商品销量或补货时使用"),
            ToolParameterSchema.optionalInteger("days", "统计天数，范围 1-90", 1, 90)
    ));

    private final SalesSummaryService salesSummaryService;

    public GetSalesSummaryTool(SalesSummaryService salesSummaryService) {
        this.salesSummaryService = salesSummaryService;
    }

    @Override public ToolDefinition definition() { return DEFINITION; }

    @Override public ToolResult execute(ToolArguments arguments) {
        Integer days = arguments.contains("days") ? arguments.getInteger("days") : 7;
        if (arguments.contains("sku")) {
            return ToolResult.success(salesSummaryService.productSales(CurrentUserContext.requireUserId(),
                    arguments.getString("sku"), days));
        }
        return ToolResult.success(salesSummaryService.summary(CurrentUserContext.requireUserId(), days));
    }
}

package com.mikuissun.ecommerceagent.tool;

import com.mikuissun.ecommerceagent.common.CurrentUserContext;
import com.mikuissun.ecommerceagent.service.OrderService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class QueryOrdersTool implements Tool {
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "query_orders", "按条件查询当前用户订单，最多返回 50 条", List.of(
            ToolParameterSchema.optionalEnum("status", "订单状态", ReadOnlyToolSupport.ORDER_STATUSES),
            ToolParameterSchema.optionalString("marketplace", "销售平台"),
            ToolParameterSchema.optionalDate("startDate", "开始日期，格式 YYYY-MM-DD"),
            ToolParameterSchema.optionalDate("endDate", "结束日期，格式 YYYY-MM-DD")
    ));

    private final OrderService orderService;

    public QueryOrdersTool(OrderService orderService) { this.orderService = orderService; }

    @Override public ToolDefinition definition() { return DEFINITION; }

    @Override public ToolResult execute(ToolArguments arguments) {
        LocalDate startDate = arguments.getDate("startDate");
        LocalDate endDate = arguments.getDate("endDate");
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return ToolResult.failure("INVALID_PARAMETER", "startDate 不能晚于 endDate");
        }
        return ToolResult.success(orderService.list(CurrentUserContext.requireUserId(), arguments.getString("status"),
                arguments.getString("marketplace"), startDate, endDate, 50));
    }
}


package com.mikuissun.ecommerceagent.tool;

public interface Tool {
    ToolDefinition definition();

    default boolean requiresApproval() { return false; }

    ToolResult execute(ToolArguments arguments);
}

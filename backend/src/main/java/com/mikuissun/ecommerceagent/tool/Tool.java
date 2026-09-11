package com.mikuissun.ecommerceagent.tool;

public interface Tool {
    ToolDefinition definition();

    ToolResult execute(ToolArguments arguments);
}


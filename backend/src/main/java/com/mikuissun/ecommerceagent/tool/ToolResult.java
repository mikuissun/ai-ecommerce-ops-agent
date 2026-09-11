package com.mikuissun.ecommerceagent.tool;

public record ToolResult(boolean success, Object data, String errorCode, String errorMessage) {
    public static ToolResult success(Object data) {
        return new ToolResult(true, data, null, null);
    }

    public static ToolResult failure(String errorCode, String errorMessage) {
        return new ToolResult(false, null, errorCode, errorMessage);
    }
}


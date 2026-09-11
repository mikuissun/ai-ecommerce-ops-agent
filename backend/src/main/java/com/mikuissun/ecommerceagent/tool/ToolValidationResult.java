package com.mikuissun.ecommerceagent.tool;

import java.util.Map;

public record ToolValidationResult(boolean valid, Map<String, Object> normalizedArguments, ToolResult error) {
    public static ToolValidationResult valid(Map<String, Object> arguments) {
        return new ToolValidationResult(true, Map.copyOf(arguments), null);
    }

    public static ToolValidationResult invalid(String code, String message) {
        return new ToolValidationResult(false, Map.of(), ToolResult.failure(code, message));
    }
}


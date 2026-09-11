package com.mikuissun.ecommerceagent.dto.tool;

import java.util.Map;

public record ToolExecuteRequest(Map<String, Object> arguments) {
    public ToolExecuteRequest {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}


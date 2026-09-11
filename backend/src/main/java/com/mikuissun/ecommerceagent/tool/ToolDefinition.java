package com.mikuissun.ecommerceagent.tool;

import java.util.List;

public record ToolDefinition(String name, String description, List<ToolParameterSchema> parameters) {
    public ToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name 不能为空");
        }
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}


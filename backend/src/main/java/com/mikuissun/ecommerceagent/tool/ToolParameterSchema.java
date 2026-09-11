package com.mikuissun.ecommerceagent.tool;

import java.util.List;

public record ToolParameterSchema(String name, ToolParameterType type, String description, boolean required,
                                  List<String> enumValues, Integer min, Integer max) {
    public ToolParameterSchema {
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }

    public static ToolParameterSchema requiredString(String name, String description) {
        return new ToolParameterSchema(name, ToolParameterType.STRING, description, true, List.of(), null, null);
    }

    public static ToolParameterSchema optionalString(String name, String description) {
        return new ToolParameterSchema(name, ToolParameterType.STRING, description, false, List.of(), null, null);
    }

    public static ToolParameterSchema optionalDate(String name, String description) {
        return new ToolParameterSchema(name, ToolParameterType.DATE, description, false, List.of(), null, null);
    }

    public static ToolParameterSchema optionalEnum(String name, String description, List<String> values) {
        return new ToolParameterSchema(name, ToolParameterType.ENUM, description, false, values, null, null);
    }

    public static ToolParameterSchema optionalInteger(String name, String description, int min, int max) {
        return new ToolParameterSchema(name, ToolParameterType.INTEGER, description, false, List.of(), min, max);
    }
}


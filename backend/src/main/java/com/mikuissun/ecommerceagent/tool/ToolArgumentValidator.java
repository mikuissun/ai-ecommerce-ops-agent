package com.mikuissun.ecommerceagent.tool;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class ToolArgumentValidator {
    public ToolValidationResult validate(ToolDefinition definition, Map<String, Object> rawArguments) {
        Map<String, Object> raw = rawArguments == null ? Map.of() : rawArguments;
        Set<String> allowed = definition.parameters().stream().map(ToolParameterSchema::name).collect(java.util.stream.Collectors.toSet());
        for (String name : raw.keySet()) {
            if (!allowed.contains(name)) {
                return ToolValidationResult.invalid("UNKNOWN_PARAMETER", "不支持参数: " + name);
            }
        }

        Map<String, Object> normalized = new HashMap<>();
        for (ToolParameterSchema parameter : definition.parameters()) {
            Object value = raw.get(parameter.name());
            if (value == null) {
                if (parameter.required()) {
                    return ToolValidationResult.invalid("MISSING_PARAMETER", "缺少必填参数: " + parameter.name());
                }
                continue;
            }
            ToolValidationResult result = normalize(parameter, value);
            if (!result.valid()) {
                return result;
            }
            normalized.put(parameter.name(), result.normalizedArguments().get(parameter.name()));
        }
        return ToolValidationResult.valid(normalized);
    }

    private ToolValidationResult normalize(ToolParameterSchema parameter, Object value) {
        return switch (parameter.type()) {
            case STRING -> normalizeString(parameter, value);
            case INTEGER -> normalizeInteger(parameter, value);
            case DATE -> normalizeDate(parameter, value);
            case ENUM -> normalizeEnum(parameter, value);
        };
    }

    private ToolValidationResult normalizeString(ToolParameterSchema parameter, Object value) {
        if (!(value instanceof String string) || string.isBlank()) {
            return ToolValidationResult.invalid("INVALID_PARAMETER", "参数 " + parameter.name() + " 必须是非空字符串");
        }
        return ToolValidationResult.valid(Map.of(parameter.name(), string.trim()));
    }

    private ToolValidationResult normalizeInteger(ToolParameterSchema parameter, Object value) {
        Integer integer;
        if (value instanceof Number number && number.doubleValue() == number.intValue()) {
            integer = number.intValue();
        } else if (value instanceof String string) {
            try {
                integer = Integer.valueOf(string.trim());
            } catch (NumberFormatException ex) {
                return ToolValidationResult.invalid("INVALID_PARAMETER", "参数 " + parameter.name() + " 必须是整数");
            }
        } else {
            return ToolValidationResult.invalid("INVALID_PARAMETER", "参数 " + parameter.name() + " 必须是整数");
        }
        if (parameter.min() != null && integer < parameter.min() || parameter.max() != null && integer > parameter.max()) {
            return ToolValidationResult.invalid("PARAMETER_OUT_OF_RANGE",
                    "参数 " + parameter.name() + " 必须在 " + parameter.min() + " 到 " + parameter.max() + " 之间");
        }
        return ToolValidationResult.valid(Map.of(parameter.name(), integer));
    }

    private ToolValidationResult normalizeDate(ToolParameterSchema parameter, Object value) {
        if (value instanceof LocalDate date) {
            return ToolValidationResult.valid(Map.of(parameter.name(), date));
        }
        if (!(value instanceof String string)) {
            return ToolValidationResult.invalid("INVALID_PARAMETER", "参数 " + parameter.name() + " 必须是 YYYY-MM-DD 日期");
        }
        try {
            return ToolValidationResult.valid(Map.of(parameter.name(), LocalDate.parse(string.trim())));
        } catch (DateTimeParseException ex) {
            return ToolValidationResult.invalid("INVALID_PARAMETER", "参数 " + parameter.name() + " 必须是 YYYY-MM-DD 日期");
        }
    }

    private ToolValidationResult normalizeEnum(ToolParameterSchema parameter, Object value) {
        if (!(value instanceof String string) || !parameter.enumValues().contains(string)) {
            return ToolValidationResult.invalid("INVALID_PARAMETER", "参数 " + parameter.name() + " 取值不合法");
        }
        return ToolValidationResult.valid(Map.of(parameter.name(), string));
    }
}


package com.mikuissun.ecommerceagent.tool;

import com.mikuissun.ecommerceagent.common.BusinessException;
import com.mikuissun.ecommerceagent.common.CurrentUserContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ToolExecutor {
    private final ToolRegistry registry;
    private final ToolArgumentValidator validator;

    public ToolExecutor(ToolRegistry registry, ToolArgumentValidator validator) {
        this.registry = registry;
        this.validator = validator;
    }

    public ToolResult execute(String toolName, Map<String, Object> rawArguments) {
        if (toolName == null || toolName.isBlank()) {
            return ToolResult.failure("INVALID_TOOL_NAME", "Tool name 不能为空");
        }
        Tool tool = registry.find(toolName).orElse(null);
        if (tool == null) {
            return ToolResult.failure("TOOL_NOT_FOUND", "未知 Tool: " + toolName);
        }
        try {
            CurrentUserContext.requireUserId();
        } catch (BusinessException ex) {
            return ToolResult.failure("UNAUTHORIZED", ex.getMessage());
        }
        ToolValidationResult validation = validator.validate(tool.definition(), rawArguments);
        if (!validation.valid()) {
            return validation.error();
        }
        try {
            return tool.execute(new ToolArguments(validation.normalizedArguments()));
        } catch (BusinessException ex) {
            return ToolResult.failure(ex.getStatus().name(), ex.getMessage());
        } catch (Exception ex) {
            return ToolResult.failure("EXECUTION_ERROR", "Tool 执行失败");
        }
    }
}


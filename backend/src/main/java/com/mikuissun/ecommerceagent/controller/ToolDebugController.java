package com.mikuissun.ecommerceagent.controller;

import com.mikuissun.ecommerceagent.common.ApiResponse;
import com.mikuissun.ecommerceagent.common.CurrentUserContext;
import com.mikuissun.ecommerceagent.dto.tool.ToolExecuteRequest;
import com.mikuissun.ecommerceagent.tool.ToolDefinition;
import com.mikuissun.ecommerceagent.tool.ToolExecutor;
import com.mikuissun.ecommerceagent.tool.ToolRegistry;
import com.mikuissun.ecommerceagent.tool.ToolResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
public class ToolDebugController {
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;

    public ToolDebugController(ToolRegistry toolRegistry, ToolExecutor toolExecutor) {
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
    }

    @GetMapping
    public ApiResponse<List<ToolDefinition>> definitions() {
        CurrentUserContext.requireUserId();
        return ApiResponse.ok(toolRegistry.definitions());
    }

    @PostMapping("/{toolName}/execute")
    public ApiResponse<ToolResult> execute(@PathVariable String toolName,
                                           @RequestBody(required = false) ToolExecuteRequest request) {
        CurrentUserContext.requireUserId();
        ToolResult result = toolExecutor.execute(toolName, request == null ? java.util.Map.of() : request.arguments());
        return ApiResponse.ok(result);
    }
}


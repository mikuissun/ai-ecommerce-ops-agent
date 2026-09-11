package com.mikuissun.ecommerceagent.tool;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {
    private final Map<String, Tool> tools;

    public ToolRegistry(List<Tool> toolBeans) {
        Map<String, Tool> registered = toolBeans.stream().collect(Collectors.toMap(
                tool -> tool.definition().name(), Function.identity(), (first, second) -> {
                    throw new IllegalStateException("重复 Tool name: " + first.definition().name());
                }));
        this.tools = Map.copyOf(registered);
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(Tool::definition)
                .sorted(Comparator.comparing(ToolDefinition::name)).toList();
    }

    public int size() { return tools.size(); }
}


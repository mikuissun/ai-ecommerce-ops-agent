package com.mikuissun.ecommerceagent.tool;

import java.time.LocalDate;
import java.util.Map;

public final class ToolArguments {
    private final Map<String, Object> values;

    public ToolArguments(Map<String, Object> values) {
        this.values = Map.copyOf(values);
    }

    public boolean contains(String name) { return values.containsKey(name); }

    public String getString(String name) { return (String) values.get(name); }

    public Integer getInteger(String name) { return (Integer) values.get(name); }

    public LocalDate getDate(String name) { return (LocalDate) values.get(name); }
}


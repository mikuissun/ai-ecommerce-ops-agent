package com.mikuissun.ecommerceagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mikuissun.ecommerceagent.tool.ToolDefinition;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ToolSchemaConverter {
    private final ObjectMapper json;
    public ToolSchemaConverter(ObjectMapper json) { this.json = json; }

    public List<JsonNode> convert(List<ToolDefinition> definitions) {
        return definitions.stream().map(this::convert).toList();
    }

    private JsonNode convert(ToolDefinition definition) {
        ObjectNode tool = json.createObjectNode().put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", definition.name()).put("description", definition.description());
        ObjectNode parameters = function.putObject("parameters").put("type", "object");
        parameters.put("additionalProperties", false);
        ObjectNode properties = parameters.putObject("properties");
        var required = parameters.putArray("required");
        for (var parameter : definition.parameters()) {
            ObjectNode property = properties.putObject(parameter.name());
            property.put("description", parameter.description());
            property.put("type", parameter.type() == com.mikuissun.ecommerceagent.tool.ToolParameterType.INTEGER
                    ? "integer" : "string");
            if (parameter.type() == com.mikuissun.ecommerceagent.tool.ToolParameterType.DATE) {
                property.put("format", "date");
            }
            if (!parameter.enumValues().isEmpty()) property.set("enum", json.valueToTree(parameter.enumValues()));
            if (parameter.min() != null) property.put("minimum", parameter.min());
            if (parameter.max() != null) property.put("maximum", parameter.max());
            if (parameter.required()) required.add(parameter.name());
        }
        return tool;
    }
}

package com.mikuissun.ecommerceagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Returns one assistant message, optionally containing tool_calls. */
public interface AgentChatModel {
    JsonNode chat(List<JsonNode> messages, List<JsonNode> tools);
}

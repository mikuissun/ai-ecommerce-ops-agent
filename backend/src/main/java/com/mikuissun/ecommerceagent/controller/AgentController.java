package com.mikuissun.ecommerceagent.controller;

import com.mikuissun.ecommerceagent.dto.agent.AgentChatRequest;
import com.mikuissun.ecommerceagent.dto.agent.AgentChatResponse;
import com.mikuissun.ecommerceagent.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final ConversationService conversations;
    public AgentController(ConversationService conversations) { this.conversations = conversations; }

    @PostMapping("/chat")
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request) {
        return conversations.chat(request.conversationId(), request.message());
    }
}

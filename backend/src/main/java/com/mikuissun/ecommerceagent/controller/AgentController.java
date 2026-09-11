package com.mikuissun.ecommerceagent.controller;

import com.mikuissun.ecommerceagent.dto.agent.AgentChatRequest;
import com.mikuissun.ecommerceagent.dto.agent.AgentChatResponse;
import com.mikuissun.ecommerceagent.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentService agent;
    public AgentController(AgentService agent) { this.agent = agent; }

    @PostMapping("/chat")
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request) {
        return agent.chat(request.message());
    }
}

package com.mikuissun.ecommerceagent.controller;

import com.mikuissun.ecommerceagent.common.ApiResponse;
import com.mikuissun.ecommerceagent.dto.conversation.*;
import com.mikuissun.ecommerceagent.service.ConversationService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService conversations;
    public ConversationController(ConversationService conversations) { this.conversations = conversations; }

    @GetMapping
    public ApiResponse<List<ConversationResponse>> list(
            @RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(conversations.list(limit, offset));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<ConversationMessageResponse>> messages(@PathVariable long id,
            @RequestParam(defaultValue = "100") int limit, @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(conversations.messages(id, limit, offset));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        conversations.delete(id);
        return ApiResponse.ok(null, "会话已删除");
    }
}

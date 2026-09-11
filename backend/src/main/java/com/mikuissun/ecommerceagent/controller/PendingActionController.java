package com.mikuissun.ecommerceagent.controller;

import com.mikuissun.ecommerceagent.dto.agent.ApprovalResponse;
import com.mikuissun.ecommerceagent.service.PendingActionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pending-actions")
public class PendingActionController {
    private final PendingActionService actions;
    public PendingActionController(PendingActionService actions) { this.actions = actions; }
    @PostMapping("/{id}/approve")
    public ApprovalResponse approve(@PathVariable long id) { return actions.approve(id); }
    @PostMapping("/{id}/reject")
    public ApprovalResponse reject(@PathVariable long id) { return actions.reject(id); }
}

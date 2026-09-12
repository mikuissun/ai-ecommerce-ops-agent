package com.mikuissun.ecommerceagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.ecommerceagent.common.*;
import com.mikuissun.ecommerceagent.dto.agent.ApprovalResponse;
import com.mikuissun.ecommerceagent.entity.PendingActionEntity;
import com.mikuissun.ecommerceagent.mapper.*;
import com.mikuissun.ecommerceagent.tool.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Map;

@Service
public class PendingActionService {
    public java.util.List<com.mikuissun.ecommerceagent.dto.agent.PendingActionResponse> list(long conversationId, int limit, int offset) {
        long userId = CurrentUserContext.requireUserId();
        if (conversations.findOwned(conversationId, userId) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        if (limit < 1 || limit > 100 || offset < 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "分页参数无效");
        return actions.listOwned(conversationId, userId, limit, offset).stream().map(action -> {
            try {
                Map<String, Object> stored = json.readValue(action.getArgumentsJson(), new TypeReference<Map<String, Object>>() {});
                Map<String, Object> safe = new java.util.LinkedHashMap<>();
                for (String key : java.util.List.of("sku", "newPrice")) {
                    if (stored.containsKey(key)) safe.put(key, stored.get(key));
                }
                return new com.mikuissun.ecommerceagent.dto.agent.PendingActionResponse(
                        action.getId(), action.getToolName(), safe, action.getStatus());
            } catch (Exception ex) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "无法读取操作参数");
            }
        }).toList();
    }

    private final PendingActionMapper actions;
    private final ConversationMapper conversations;
    private final ProductService products;
    private final ToolRegistry registry;
    private final ToolArgumentValidator validator;
    private final ObjectMapper json;

    public PendingActionService(PendingActionMapper actions, ConversationMapper conversations,
                                ProductService products, ToolRegistry registry,
                                ToolArgumentValidator validator, ObjectMapper json) {
        this.actions = actions;
        this.conversations = conversations;
        this.products = products;
        this.registry = registry;
        this.validator = validator;
        this.json = json;
    }

    @Transactional
    public PendingActionEntity create(Long conversationId, String toolName, Map<String, Object> arguments) {
        long userId = CurrentUserContext.requireUserId();
        if (conversationId == null || conversations.lockOwned(conversationId, userId) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        if (!"update_product_price".equals(toolName)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的写操作");
        }
        var validation = validator.validate(registry.find(toolName).orElseThrow().definition(), arguments);
        if (!validation.valid()) throw new BusinessException(HttpStatus.BAD_REQUEST, validation.error().errorMessage());
        var normalized = validation.normalizedArguments();
        String sku = (String) normalized.get("sku");
        BigDecimal price = (BigDecimal) normalized.get("newPrice");
        ProductService.validatePrice(price);
        products.getBySku(userId, sku);
        var action = new PendingActionEntity();
        action.setUserId(userId);
        action.setConversationId(conversationId);
        action.setToolName(toolName);
        try {
            action.setArgumentsJson(json.writeValueAsString(normalized));
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "参数无法保存");
        }
        action.setStatus("PENDING");
        actions.insert(action);
        return action;
    }

    @Transactional
    public ApprovalResponse approve(long id) {
        long userId = CurrentUserContext.requireUserId();
        var action = pendingOwned(id, userId);
        actions.status(id, userId, "APPROVED");
        String sku = null;
        BigDecimal price;
        // Validate immutable stored arguments again, never accept replacement arguments from the API.
        try {
            if (!"update_product_price".equals(action.getToolName())) throw new IllegalArgumentException();
            Map<String, Object> args = json.readerFor(new TypeReference<Map<String, Object>>() {})
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(action.getArgumentsJson());
            var validation = validator.validate(registry.find(action.getToolName()).orElseThrow().definition(), args);
            if (!validation.valid()) throw new IllegalArgumentException();
            sku = (String) validation.normalizedArguments().get("sku");
            price = (BigDecimal) validation.normalizedArguments().get("newPrice");
            ProductService.validatePrice(price);
        } catch (Exception ex) {
            return failed(id, userId, sku, "保存的操作参数无效");
        }
        com.mikuissun.ecommerceagent.dto.product.PriceChangeResponse result;
        try {
            result = products.updatePrice(sku, price);
        } catch (BusinessException ex) {
            return failed(id, userId, sku, ex.getMessage());
        }
        actions.executed(id, userId);
        actions.audit(id, userId, sku, result.beforePrice().toPlainString(), result.afterPrice().toPlainString(), "SUCCESS");
        return new ApprovalResponse(id, "EXECUTED", result, null);
    }

    @Transactional
    public ApprovalResponse reject(long id) {
        long userId = CurrentUserContext.requireUserId();
        pendingOwned(id, userId);
        actions.status(id, userId, "REJECTED");
        return new ApprovalResponse(id, "REJECTED", null, null);
    }

    private PendingActionEntity pendingOwned(long id, long userId) {
        var action = actions.lockOwned(id, userId);
        if (action == null) throw new BusinessException(HttpStatus.NOT_FOUND, "待确认操作不存在");
        if (!"PENDING".equals(action.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "操作已处理，不能重复审批");
        }
        return action;
    }

    private ApprovalResponse failed(long id, long userId, String sku, String message) {
        actions.status(id, userId, "FAILED");
        actions.audit(id, userId, sku == null ? "unknown" : sku, null, null, "FAILED");
        return new ApprovalResponse(id, "FAILED", null, message);
    }
}

package com.mikuissun.ecommerceagent.service;

import com.mikuissun.ecommerceagent.common.*;
import com.mikuissun.ecommerceagent.dto.agent.AgentChatResponse;
import com.mikuissun.ecommerceagent.dto.conversation.*;
import com.mikuissun.ecommerceagent.entity.ConversationEntity;
import com.mikuissun.ecommerceagent.mapper.ConversationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ConversationService {
    private final ConversationMapper conversations;
    private final AgentService agent;
    private final int maxHistoryMessages;
    private final TransactionTemplate shortTransaction;

    public ConversationService(ConversationMapper conversations, AgentService agent,
                               @Value("${agent.max-history-messages:20}") int maxHistoryMessages,
                               PlatformTransactionManager transactionManager) {
        if (maxHistoryMessages < 1 || maxHistoryMessages > 100) {
            throw new IllegalArgumentException("AGENT_MAX_HISTORY_MESSAGES 必须在 1 到 100 之间");
        }
        this.conversations = conversations;
        this.agent = agent;
        this.maxHistoryMessages = maxHistoryMessages;
        this.shortTransaction = new TransactionTemplate(transactionManager);
        this.shortTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // No outer transaction/synchronization scope: Tool reads must release their connections too.
    public AgentChatResponse chat(Long conversationId, String message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Conversation chat 必须从数据库事务之外调用");
        }
        long userId = CurrentUserContext.requireUserId();
        if (message == null || message.isBlank() || message.length() > 10000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "message 必须为 1 到 10000 字符");
        }
        PreparedTurn turn = shortTransaction.execute(status -> prepare(conversationId, userId, message));
        try {
            var result = agent.chat(turn.history());
            shortTransaction.executeWithoutResult(status -> {
                if (conversations.lockOwned(turn.id(), userId) == null) throw notFound();
                conversations.append(turn.id(), userId, "ASSISTANT", result.answer());
                conversations.touch(turn.id(), userId);
            });
            return new AgentChatResponse(turn.id(), result.answer(), result.toolCalls());
        } catch (RuntimeException failure) {
            try {
                shortTransaction.executeWithoutResult(status -> {
                    if (conversations.lockOwned(turn.id(), userId) == null) return;
                    conversations.removeFailedUserMessage(turn.id(), userId, turn.userMessageId());
                    if (conversationId == null) conversations.removeEmptyConversation(turn.id(), userId);
                });
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private PreparedTurn prepare(Long conversationId, long userId, String message) {
        long id;
        if (conversationId == null) {
            var conversation = new ConversationEntity();
            conversation.setUserId(userId);
            String title = message.strip();
            int titleEnd = title.offsetByCodePoints(0, Math.min(100, title.codePointCount(0, title.length())));
            conversation.setTitle(title.substring(0, titleEnd));
            conversation.setCreatedAt(LocalDateTime.now());
            conversation.setUpdatedAt(conversation.getCreatedAt());
            conversations.insert(conversation);
            id = conversation.getId();
        } else {
            if (conversations.lockOwned(conversationId, userId) == null) throw notFound();
            id = conversationId;
        }
        conversations.append(id, userId, "USER", message);
        var history = new ArrayList<>(conversations.recentOwned(id, userId, maxHistoryMessages));
        long userMessageId = history.get(0).id();
        Collections.reverse(history);
        return new PreparedTurn(id, userMessageId, List.copyOf(history));
    }

    private record PreparedTurn(long id, long userMessageId, List<ConversationMessageResponse> history) {}

    public List<ConversationResponse> list(int limit, int offset) {
        long userId = CurrentUserContext.requireUserId();
        validatePage(limit, offset);
        return conversations.listOwned(userId, limit, offset);
    }

    public List<ConversationMessageResponse> messages(long id, int limit, int offset) {
        long userId = CurrentUserContext.requireUserId();
        validatePage(limit, offset);
        if (conversations.findOwned(id, userId) == null) throw notFound();
        return conversations.messagesOwned(id, userId, limit, offset);
    }

    private void validatePage(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "limit 必须在 1 到 100 之间，offset 不能为负数");
        }
    }

    private BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "会话不存在");
    }
}

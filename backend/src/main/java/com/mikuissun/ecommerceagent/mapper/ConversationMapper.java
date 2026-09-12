package com.mikuissun.ecommerceagent.mapper;

import com.mikuissun.ecommerceagent.dto.conversation.*;
import com.mikuissun.ecommerceagent.entity.ConversationEntity;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ConversationMapper {
    @Insert("INSERT INTO conversations(user_id, title, created_at, updated_at) VALUES(#{userId}, #{title}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ConversationEntity conversation);

    @Select("SELECT * FROM conversations WHERE id=#{id} AND user_id=#{userId} FOR UPDATE")
    ConversationEntity lockOwned(long id, long userId);

    @Select("SELECT * FROM conversations WHERE id=#{id} AND user_id=#{userId}")
    ConversationEntity findOwned(long id, long userId);

    @Update("UPDATE pending_actions SET conversation_id=NULL, " +
            "status=CASE WHEN status='PENDING' THEN 'REJECTED' ELSE status END " +
            "WHERE conversation_id=#{id} AND user_id=#{userId}")
    int detachActions(long id, long userId);

    @Delete("DELETE FROM conversation_messages WHERE conversation_id=#{id} " +
            "AND EXISTS (SELECT 1 FROM conversations WHERE id=#{id} AND user_id=#{userId})")
    int deleteMessagesOwned(long id, long userId);

    @Delete("DELETE FROM conversations WHERE id=#{id} AND user_id=#{userId}")
    int deleteOwned(long id, long userId);

    @Insert("INSERT INTO conversation_messages(conversation_id, role, content) " +
            "SELECT id, #{role}, #{content} FROM conversations WHERE id=#{id} AND user_id=#{userId}")
    int append(long id, long userId, String role, String content);

    @Update("UPDATE conversations SET updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND user_id=#{userId}")
    int touch(long id, long userId);

    @Delete("DELETE FROM conversation_messages WHERE id=#{messageId} AND conversation_id=#{id} AND role='USER' " +
            "AND EXISTS (SELECT 1 FROM conversations WHERE id=#{id} AND user_id=#{userId})")
    int removeFailedUserMessage(long id, long userId, long messageId);

    @Delete("DELETE FROM conversations WHERE id=#{id} AND user_id=#{userId} " +
            "AND NOT EXISTS (SELECT 1 FROM conversation_messages WHERE conversation_id=#{id}) " +
            "AND NOT EXISTS (SELECT 1 FROM pending_actions WHERE conversation_id=#{id})")
    int removeEmptyConversation(long id, long userId);

    @Select("SELECT id, title, created_at, updated_at FROM conversations WHERE user_id=#{userId} " +
            "ORDER BY updated_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ConversationResponse> listOwned(long userId, int limit, int offset);

    @Select("SELECT m.id, m.role, m.content, m.created_at FROM conversation_messages m " +
            "JOIN conversations c ON c.id=m.conversation_id " +
            "WHERE c.id=#{id} AND c.user_id=#{userId} ORDER BY m.id DESC LIMIT #{limit}")
    List<ConversationMessageResponse> recentOwned(long id, long userId, int limit);

    @Select("SELECT m.id, m.role, m.content, m.created_at FROM conversation_messages m " +
            "JOIN conversations c ON c.id=m.conversation_id " +
            "WHERE c.id=#{id} AND c.user_id=#{userId} ORDER BY m.id ASC LIMIT #{limit} OFFSET #{offset}")
    List<ConversationMessageResponse> messagesOwned(long id, long userId, int limit, int offset);
}

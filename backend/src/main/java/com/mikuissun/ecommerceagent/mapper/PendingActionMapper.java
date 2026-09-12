package com.mikuissun.ecommerceagent.mapper;

import com.mikuissun.ecommerceagent.entity.PendingActionEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PendingActionMapper {
    @Select("SELECT * FROM pending_actions WHERE conversation_id=#{conversationId} AND user_id=#{userId} " +
            "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    java.util.List<PendingActionEntity> listOwned(long conversationId, long userId, int limit, int offset);

    @Insert("INSERT INTO pending_actions(user_id,conversation_id,tool_name,arguments_json,status) " +
            "VALUES(#{userId},#{conversationId},#{toolName},#{argumentsJson},'PENDING')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PendingActionEntity action);

    @Select("SELECT * FROM pending_actions WHERE id=#{id} AND user_id=#{userId} FOR UPDATE")
    PendingActionEntity lockOwned(long id, long userId);

    @Update("UPDATE pending_actions SET status=#{status} WHERE id=#{id} AND user_id=#{userId}")
    int status(long id, long userId, String status);

    @Update("UPDATE pending_actions SET status='EXECUTED',executed_at=CURRENT_TIMESTAMP " +
            "WHERE id=#{id} AND user_id=#{userId} AND status='APPROVED'")
    int executed(long id, long userId);

    @Insert("INSERT INTO audit_logs(pending_action_id,user_id,action_type,target,before_value,after_value,status) " +
            "VALUES(#{id},#{userId},'update_product_price',#{sku},#{before},#{after},#{status})")
    int audit(long id, long userId, String sku, String before, String after, String status);
}

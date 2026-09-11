package com.mikuissun.ecommerceagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikuissun.ecommerceagent.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {
    @Select("SELECT COUNT(*) FROM orders WHERE user_id = #{userId} AND ordered_at >= #{from}")
    long countSince(Long userId, LocalDateTime from);

    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE user_id = #{userId} " +
            "AND ordered_at >= #{from} AND status IN ('PAID', 'SHIPPED', 'COMPLETED')")
    BigDecimal revenueSince(Long userId, LocalDateTime from);

    @Select("SELECT COUNT(*) FROM orders WHERE user_id = #{userId} AND status = #{status}")
    long countByStatus(Long userId, String status);

    @Select("SELECT COUNT(*) FROM orders WHERE user_id = #{userId} AND ordered_at >= #{from} AND status = #{status}")
    long countByStatusSince(Long userId, LocalDateTime from, String status);
}

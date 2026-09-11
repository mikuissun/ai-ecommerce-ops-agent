package com.mikuissun.ecommerceagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikuissun.ecommerceagent.entity.OrderItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemEntity> {
    @Select("SELECT oi.sku AS sku, p.name AS product_name, SUM(oi.quantity) AS units_sold, " +
            "SUM(oi.subtotal) AS revenue " +
            "FROM order_items oi " +
            "JOIN orders o ON o.id = oi.order_id " +
            "JOIN products p ON p.id = oi.product_id AND p.user_id = o.user_id " +
            "WHERE o.user_id = #{userId} AND o.ordered_at >= #{from} " +
            "AND o.status IN ('PAID', 'SHIPPED', 'COMPLETED') " +
            "GROUP BY oi.sku, p.name " +
            "ORDER BY units_sold DESC, revenue DESC LIMIT #{limit}")
    List<TopSellingProductRow> topSellingProducts(Long userId, LocalDateTime from, int limit);
}

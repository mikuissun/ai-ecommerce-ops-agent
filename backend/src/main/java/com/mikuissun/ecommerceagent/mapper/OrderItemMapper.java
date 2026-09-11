package com.mikuissun.ecommerceagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikuissun.ecommerceagent.entity.OrderItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemEntity> {
    @Select("SELECT COALESCE(SUM(oi.quantity), 0) AS units_sold, " +
            "COALESCE(SUM(oi.subtotal), 0) AS revenue, COUNT(DISTINCT o.id) AS order_count " +
            "FROM order_items oi JOIN orders o ON o.id = oi.order_id " +
            "JOIN products p ON p.id = oi.product_id AND p.user_id = o.user_id " +
            "WHERE o.user_id = #{userId} AND p.id = #{productId} " +
            "AND o.ordered_at >= #{from} AND o.ordered_at <= #{to} " +
            "AND o.status IN ('PAID', 'SHIPPED', 'COMPLETED')")
    ProductSalesRow productSales(long userId, long productId, LocalDateTime from, LocalDateTime to);

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

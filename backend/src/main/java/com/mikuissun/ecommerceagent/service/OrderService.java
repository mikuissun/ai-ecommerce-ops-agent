package com.mikuissun.ecommerceagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mikuissun.ecommerceagent.common.BusinessException;
import com.mikuissun.ecommerceagent.dto.order.OrderResponse;
import com.mikuissun.ecommerceagent.entity.OrderEntity;
import com.mikuissun.ecommerceagent.mapper.OrderMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {
    private final OrderMapper orderMapper;

    public OrderService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    public List<OrderResponse> list(long userId, String status, String marketplace, LocalDate startDate, LocalDate endDate) {
        return list(userId, status, marketplace, startDate, endDate, 1000);
    }

    public List<OrderResponse> list(long userId, String status, String marketplace, LocalDate startDate, LocalDate endDate, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        LambdaQueryWrapper<OrderEntity> query = new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getUserId, userId)
                .eq(status != null && !status.isBlank(), OrderEntity::getStatus, status)
                .eq(marketplace != null && !marketplace.isBlank(), OrderEntity::getMarketplace, marketplace)
                .ge(startDate != null, OrderEntity::getOrderedAt, startDate == null ? null : startDate.atStartOfDay())
                .lt(endDate != null, OrderEntity::getOrderedAt, endDate == null ? null : endDate.plusDays(1).atStartOfDay())
                .orderByDesc(OrderEntity::getOrderedAt)
                .last("LIMIT " + safeLimit);
        return orderMapper.selectList(query).stream().map(OrderResponse::from).toList();
    }

    public OrderResponse get(long userId, long id) {
        OrderEntity order = orderMapper.selectOne(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getId, id).eq(OrderEntity::getUserId, userId));
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        return OrderResponse.from(order);
    }

    public long count(long userId) {
        return orderMapper.selectCount(new LambdaQueryWrapper<OrderEntity>().eq(OrderEntity::getUserId, userId));
    }

    public long recent7DaysOrderCount(long userId) {
        return orderMapper.countSince(userId, LocalDateTime.now().minusDays(7));
    }

    public java.math.BigDecimal recent7DaysRevenue(long userId) {
        return orderMapper.revenueSince(userId, LocalDateTime.now().minusDays(7));
    }

    public long countByStatus(long userId, String status) {
        return orderMapper.countByStatus(userId, status);
    }
}

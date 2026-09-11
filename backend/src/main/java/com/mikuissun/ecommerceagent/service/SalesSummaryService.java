package com.mikuissun.ecommerceagent.service;

import com.mikuissun.ecommerceagent.dto.analytics.SalesSummaryResponse;
import com.mikuissun.ecommerceagent.dto.analytics.TopSellingProductResponse;
import com.mikuissun.ecommerceagent.mapper.OrderItemMapper;
import com.mikuissun.ecommerceagent.mapper.OrderMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SalesSummaryService {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    public SalesSummaryService(OrderMapper orderMapper, OrderItemMapper orderItemMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    public SalesSummaryResponse summary(long userId, int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        var topSellingProducts = orderItemMapper.topSellingProducts(userId, from, 10).stream()
                .map(row -> new TopSellingProductResponse(row.getSku(), row.getProductName(),
                        row.getUnitsSold() == null ? 0 : row.getUnitsSold(), row.getRevenue()))
                .toList();
        return new SalesSummaryResponse(
                orderMapper.countSince(userId, from),
                orderMapper.revenueSince(userId, from),
                orderMapper.countByStatusSince(userId, from, "COMPLETED"),
                orderMapper.countByStatusSince(userId, from, "REFUNDED"),
                topSellingProducts
        );
    }
}


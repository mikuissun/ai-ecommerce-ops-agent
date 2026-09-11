package com.mikuissun.ecommerceagent.service;

import com.mikuissun.ecommerceagent.dto.analytics.SalesSummaryResponse;
import com.mikuissun.ecommerceagent.dto.analytics.ProductSalesResponse;
import com.mikuissun.ecommerceagent.common.BusinessException;
import org.springframework.http.HttpStatus;
import com.mikuissun.ecommerceagent.dto.analytics.TopSellingProductResponse;
import com.mikuissun.ecommerceagent.mapper.OrderItemMapper;
import com.mikuissun.ecommerceagent.mapper.OrderMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SalesSummaryService {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductService productService;

    public SalesSummaryService(OrderMapper orderMapper, OrderItemMapper orderItemMapper, ProductService productService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productService = productService;
    }

    public ProductSalesResponse productSales(long userId, String sku, int days) {
        if (days < 1 || days > 90) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "days 必须在 1 到 90 之间");
        }
        var product = productService.getBySku(userId, sku);
        LocalDateTime to = LocalDateTime.now();
        var row = orderItemMapper.productSales(userId, product.id(), to.minusDays(days), to);
        return new ProductSalesResponse(
                product.sku(), days, row.getUnitsSold(), row.getRevenue(), row.getOrderCount(), product.currency());
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

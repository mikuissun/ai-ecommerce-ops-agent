package com.mikuissun.ecommerceagent.controller;

import com.mikuissun.ecommerceagent.common.ApiResponse;
import com.mikuissun.ecommerceagent.common.CurrentUserContext;
import com.mikuissun.ecommerceagent.dto.order.OrderResponse;
import com.mikuissun.ecommerceagent.service.OrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) { this.orderService = orderService; }

    @GetMapping
    public ApiResponse<List<OrderResponse>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String marketplace,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.ok(orderService.list(CurrentUserContext.requireUserId(), status, marketplace, startDate, endDate));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(@PathVariable long id) {
        return ApiResponse.ok(orderService.get(CurrentUserContext.requireUserId(), id));
    }
}

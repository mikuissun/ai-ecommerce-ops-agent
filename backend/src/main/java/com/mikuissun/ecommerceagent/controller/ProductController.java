package com.mikuissun.ecommerceagent.controller;

import com.mikuissun.ecommerceagent.common.ApiResponse;
import com.mikuissun.ecommerceagent.common.CurrentUserContext;
import com.mikuissun.ecommerceagent.dto.product.ProductResponse;
import com.mikuissun.ecommerceagent.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) { this.productService = productService; }

    @GetMapping
    public ApiResponse<List<ProductResponse>> list(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String marketplace,
                                                   @RequestParam(required = false) String status) {
        return ApiResponse.ok(productService.list(CurrentUserContext.requireUserId(), keyword, marketplace, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable long id) {
        return ApiResponse.ok(productService.get(CurrentUserContext.requireUserId(), id));
    }
}

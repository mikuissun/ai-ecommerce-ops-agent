package com.mikuissun.ecommerceagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("products")
public class ProductEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sku;
    private String name;
    private String marketplace;
    private String category;
    private BigDecimal price;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.mikuissun.ecommerceagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class OrderEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String orderNo;
    private String marketplace;
    private String customerCountry;
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDateTime orderedAt;
    private LocalDateTime createdAt;
}

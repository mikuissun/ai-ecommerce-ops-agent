package com.mikuissun.ecommerceagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inventory")
public class InventoryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long productId;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Integer reorderThreshold;
    private LocalDateTime updatedAt;
}

package com.mikuissun.ecommerceagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mikuissun.ecommerceagent.common.BusinessException;
import com.mikuissun.ecommerceagent.dto.inventory.InventoryResponse;
import com.mikuissun.ecommerceagent.entity.InventoryEntity;
import com.mikuissun.ecommerceagent.mapper.InventoryMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryService {
    private final InventoryMapper inventoryMapper;

    public InventoryService(InventoryMapper inventoryMapper) {
        this.inventoryMapper = inventoryMapper;
    }

    public List<InventoryResponse> list(long userId, boolean lowStock) {
        LambdaQueryWrapper<InventoryEntity> query = new LambdaQueryWrapper<InventoryEntity>()
                .eq(InventoryEntity::getUserId, userId)
                .apply(lowStock, "available_quantity <= reorder_threshold")
                .orderByAsc(InventoryEntity::getAvailableQuantity);
        return inventoryMapper.selectList(query).stream().map(InventoryResponse::from).toList();
    }

    public InventoryResponse getByProduct(long userId, long productId) {
        InventoryEntity inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<InventoryEntity>()
                .eq(InventoryEntity::getUserId, userId).eq(InventoryEntity::getProductId, productId));
        if (inventory == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "库存记录不存在");
        }
        return InventoryResponse.from(inventory);
    }

    public long lowStockCount(long userId) {
        return inventoryMapper.selectCount(new LambdaQueryWrapper<InventoryEntity>()
                .eq(InventoryEntity::getUserId, userId)
                .apply("available_quantity <= reorder_threshold"));
    }
}

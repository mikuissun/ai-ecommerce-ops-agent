package com.mikuissun.ecommerceagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mikuissun.ecommerceagent.common.BusinessException;
import com.mikuissun.ecommerceagent.dto.inventory.InventoryResponse;
import com.mikuissun.ecommerceagent.dto.inventory.LowStockProductResponse;
import com.mikuissun.ecommerceagent.entity.InventoryEntity;
import com.mikuissun.ecommerceagent.entity.ProductEntity;
import com.mikuissun.ecommerceagent.mapper.InventoryMapper;
import com.mikuissun.ecommerceagent.mapper.ProductMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryService {
    private final InventoryMapper inventoryMapper;
    private final ProductMapper productMapper;

    public InventoryService(InventoryMapper inventoryMapper, ProductMapper productMapper) {
        this.inventoryMapper = inventoryMapper;
        this.productMapper = productMapper;
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

    public List<LowStockProductResponse> listLowStock(long userId, String marketplace, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<ProductEntity> products = productMapper.selectList(new LambdaQueryWrapper<ProductEntity>()
                .eq(ProductEntity::getUserId, userId)
                .eq(marketplace != null && !marketplace.isBlank(), ProductEntity::getMarketplace, marketplace));
        if (products.isEmpty()) {
            return List.of();
        }
        var productById = products.stream().collect(java.util.stream.Collectors.toMap(ProductEntity::getId, p -> p));
        List<InventoryEntity> inventory = inventoryMapper.selectList(new LambdaQueryWrapper<InventoryEntity>()
                .eq(InventoryEntity::getUserId, userId)
                .in(InventoryEntity::getProductId, productById.keySet())
                .apply("available_quantity <= reorder_threshold")
                .orderByAsc(InventoryEntity::getAvailableQuantity)
                .last("LIMIT " + safeLimit));
        return inventory.stream().map(i -> {
            ProductEntity product = productById.get(i.getProductId());
            return new LowStockProductResponse(product.getId(), product.getSku(), product.getName(),
                    product.getMarketplace(), i.getAvailableQuantity(), i.getReservedQuantity(),
                    i.getReorderThreshold(), true);
        }).toList();
    }
}

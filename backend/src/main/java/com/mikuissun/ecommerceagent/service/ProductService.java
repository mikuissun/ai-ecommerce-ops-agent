package com.mikuissun.ecommerceagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mikuissun.ecommerceagent.common.BusinessException;
import com.mikuissun.ecommerceagent.dto.product.ProductResponse;
import com.mikuissun.ecommerceagent.entity.ProductEntity;
import com.mikuissun.ecommerceagent.mapper.ProductMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {
    private final ProductMapper productMapper;

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    public List<ProductResponse> list(long userId, String keyword, String marketplace, String status) {
        return list(userId, keyword, marketplace, status, 1000);
    }

    public List<ProductResponse> list(long userId, String keyword, String marketplace, String status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        LambdaQueryWrapper<ProductEntity> query = new LambdaQueryWrapper<ProductEntity>()
                .eq(ProductEntity::getUserId, userId)
                .and(keyword != null && !keyword.isBlank(), nested -> nested
                        .like(ProductEntity::getName, keyword)
                        .or()
                        .like(ProductEntity::getSku, keyword))
                .eq(marketplace != null && !marketplace.isBlank(), ProductEntity::getMarketplace, marketplace)
                .eq(status != null && !status.isBlank(), ProductEntity::getStatus, status)
                .orderByDesc(ProductEntity::getUpdatedAt)
                .last("LIMIT " + safeLimit);
        return productMapper.selectList(query).stream().map(ProductResponse::from).toList();
    }

    public ProductResponse getBySku(long userId, String sku) {
        ProductEntity product = productMapper.selectOne(new LambdaQueryWrapper<ProductEntity>()
                .eq(ProductEntity::getUserId, userId).eq(ProductEntity::getSku, sku));
        if (product == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return ProductResponse.from(product);
    }

    public ProductResponse get(long userId, long id) {
        ProductEntity product = productMapper.selectOne(new LambdaQueryWrapper<ProductEntity>()
                .eq(ProductEntity::getId, id).eq(ProductEntity::getUserId, userId));
        if (product == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return ProductResponse.from(product);
    }

    public long count(long userId) {
        return productMapper.selectCount(new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getUserId, userId));
    }

    public static void validatePrice(java.math.BigDecimal price) {
        if (price == null || price.signum() <= 0 || price.stripTrailingZeros().scale() > 2
                || price.compareTo(new java.math.BigDecimal("99999999999999999.99")) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "价格必须大于 0、最多两位小数且不超过数据库金额范围");
        }
    }

    // Only the approval service exposes this operation through HTTP; no model-facing execution path.
    public com.mikuissun.ecommerceagent.dto.product.PriceChangeResponse updatePrice(String sku, java.math.BigDecimal price) {
        long userId = com.mikuissun.ecommerceagent.common.CurrentUserContext.requireUserId();
        validatePrice(price);
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("价格更新必须位于审批事务内");
        }
        var product = productMapper.lockBySku(userId, sku);
        if (product == null) throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        var before = product.getPrice();
        int changed = productMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProductEntity>()
                .eq(ProductEntity::getId, product.getId()).eq(ProductEntity::getUserId, userId)
                .set(ProductEntity::getPrice, price)
                .set(ProductEntity::getUpdatedAt, java.time.LocalDateTime.now()));
        if (changed != 1) throw new IllegalStateException("商品价格未更新");
        return new com.mikuissun.ecommerceagent.dto.product.PriceChangeResponse(product.getSku(), before, price);
    }
}

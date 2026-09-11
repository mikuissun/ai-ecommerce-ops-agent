package com.mikuissun.ecommerceagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikuissun.ecommerceagent.entity.ProductEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<ProductEntity> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM products WHERE user_id=#{userId} AND sku=#{sku} FOR UPDATE")
    ProductEntity lockBySku(long userId, String sku);
}

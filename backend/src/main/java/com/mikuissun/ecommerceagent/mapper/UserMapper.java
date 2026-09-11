package com.mikuissun.ecommerceagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikuissun.ecommerceagent.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
    @Select("SELECT * FROM users WHERE email = #{email} LIMIT 1")
    UserEntity findByEmail(String email);
}

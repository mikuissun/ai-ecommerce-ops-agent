package com.mikuissun.ecommerceagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mikuissun.ecommerceagent.common.BusinessException;
import com.mikuissun.ecommerceagent.dto.auth.LoginRequest;
import com.mikuissun.ecommerceagent.dto.auth.RegisterRequest;
import com.mikuissun.ecommerceagent.entity.UserEntity;
import com.mikuissun.ecommerceagent.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserEntity register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userMapper.findByEmail(email) != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "邮箱已注册");
        }
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(request.name().trim());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    public UserEntity authenticate(LoginRequest request) {
        UserEntity user = userMapper.findByEmail(request.email().trim().toLowerCase());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "邮箱或密码错误");
        }
        return user;
    }

    public UserEntity findById(long id) {
        return userMapper.selectById(id);
    }
}

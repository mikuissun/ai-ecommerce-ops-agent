package com.mikuissun.ecommerceagent.auth;

import com.mikuissun.ecommerceagent.common.ApiResponse;
import com.mikuissun.ecommerceagent.dto.auth.AuthResponse;
import com.mikuissun.ecommerceagent.dto.auth.LoginRequest;
import com.mikuissun.ecommerceagent.dto.auth.RegisterRequest;
import com.mikuissun.ecommerceagent.entity.UserEntity;
import com.mikuissun.ecommerceagent.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final JwtTokenService tokenService;

    public AuthController(UserService userService, JwtTokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(toResponse(userService.register(request)), "注册成功");
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(toResponse(userService.authenticate(request)), "登录成功");
    }

    private AuthResponse toResponse(UserEntity user) {
        return new AuthResponse("Bearer", tokenService.issueToken(user.getId(), user.getEmail()),
                user.getId(), user.getEmail(), user.getName());
    }
}

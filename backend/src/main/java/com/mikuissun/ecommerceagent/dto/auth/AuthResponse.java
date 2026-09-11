package com.mikuissun.ecommerceagent.dto.auth;

public record AuthResponse(String tokenType, String accessToken, Long userId, String email, String name) {}

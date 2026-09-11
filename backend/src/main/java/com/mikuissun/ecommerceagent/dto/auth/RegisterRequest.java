package com.mikuissun.ecommerceagent.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
        @NotBlank(message = "密码不能为空") @Size(min = 8, max = 72, message = "密码长度需为 8-72 位") String password,
        @NotBlank(message = "姓名不能为空") @Size(max = 100, message = "姓名不能超过 100 个字符") String name
) {}

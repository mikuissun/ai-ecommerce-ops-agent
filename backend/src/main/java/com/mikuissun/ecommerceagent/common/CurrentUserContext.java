package com.mikuissun.ecommerceagent.common;

import org.springframework.http.HttpStatus;

public final class CurrentUserContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private CurrentUserContext() {}

    public static void setUserId(Long userId) { USER_ID.set(userId); }

    public static Long getUserId() { return USER_ID.get(); }

    public static long requireUserId() {
        Long userId = USER_ID.get();
        if (userId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }

    public static void clear() { USER_ID.remove(); }
}

package com.mikuissun.ecommerceagent.tool;

import java.util.List;

public final class ReadOnlyToolSupport {
    public static final List<String> ORDER_STATUSES = List.of(
            "PENDING", "PAID", "SHIPPED", "COMPLETED", "CANCELLED", "REFUNDED");

    private ReadOnlyToolSupport() {}
}


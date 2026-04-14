package com.java.ecommerce.product;

public record ProductCountResponse(
        long total,
        long active,
        long inactive,
        String cacheKey) {
}

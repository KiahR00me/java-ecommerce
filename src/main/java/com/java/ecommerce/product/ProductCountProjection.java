package com.java.ecommerce.product;

public interface ProductCountProjection {
    Long getTotalCount();

    Long getActiveCount();

    Long getInactiveCount();
}

package com.java.ecommerce.product;

import java.util.List;

public record ProductCursorPage(
        List<Product> items,
        String nextCursor,
        String snapshotToken,
        String snapshotVersion,
        long snapshotIssuedAtEpochMs,
        long snapshotExpiresAtEpochMs,
        boolean snapshotActive,
        boolean hasNext,
        int limit,
        ProductSortBy sortBy,
        String sortDirection) {
}

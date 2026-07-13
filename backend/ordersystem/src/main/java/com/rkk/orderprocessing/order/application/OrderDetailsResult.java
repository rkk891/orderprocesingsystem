package com.rkk.orderprocessing.order.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Detached application result containing a complete order. */
public record OrderDetailsResult(
        UUID id,
        String status,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt) {

    /** Ensures controller serialization never observes a mutable persistence collection. */
    public OrderDetailsResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        items = List.copyOf(items);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Detached item result in submitted position order. */
    public record Item(String productId, int quantity) {}
}

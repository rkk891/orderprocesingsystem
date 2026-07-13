package com.rkk.orderprocessing.order.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Detached bounded page of order summaries. */
public record OrderPageResult(
        List<Summary> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    /** Copies the page content so it can safely cross the transaction boundary. */
    public OrderPageResult {
        content = List.copyOf(content);
    }

    /** Summary projection exposed by the application without a JPA dependency. */
    public record Summary(
            UUID id,
            String status,
            long itemCount,
            Instant createdAt,
            Instant updatedAt) {}
}

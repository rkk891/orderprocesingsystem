package com.rkk.orderprocessing.order.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Complete order-detail response. */
public record OrderResponse(
        UUID id,
        String status,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt) {

    /** Prevents later mutation of a response after it leaves the mapper. */
    public OrderResponse {
        items = List.copyOf(items);
    }
}

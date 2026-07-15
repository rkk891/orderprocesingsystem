package com.rkk.orderprocessing.order.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Complete order-detail response.
 *
 * @param id the unique identifier of the order.
 * @param status the string representation of the current order status.
 * @param items the full list of line items in the order.
 * @param createdAt the instant the order was originally placed.
 * @param updatedAt the instant the order was last modified.
 */
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

package com.rkk.orderprocessing.order.application.result;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Complete order data returned by the application without exposing a JPA entity.
 *
 * @param id the order ID
 * @param status the current order status
 * @param items the order items in submitted order
 * @param createdAt when the order was created
 * @param updatedAt when the order was last updated
 */
public record OrderDetails(
        UUID id,
        String status,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt) {

    /** Copies the item list so the result stays unchanged after the transaction closes. */
    public OrderDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        items = List.copyOf(items);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * One item returned with the order.
     *
     * @param productId the product identifier
     * @param quantity the ordered quantity
     */
    public record Item(String productId, int quantity) {}
}

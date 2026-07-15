package com.rkk.orderprocessing.order.application.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data passed to {@code OrderService} when creating an order.
 *
 * <p>This record only carries data; it does not decide whether the data is valid. Validation stays
 * in {@code OrderService} so HTTP callers and other callers follow the same rules and receive the
 * same errors.</p>
 *
 * @param items the items requested for the new order; invalid or null values are kept for service validation
 */
public record CreateOrderData(List<Item> items) {

    /**
     * Copies the item list so the caller cannot change this data after creating it.
     * Null values are kept so {@code OrderService} can return the normal validation error.
     */
    public CreateOrderData {
        items = items == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(items));
    }

    /**
     * One product requested in the new order.
     *
     * @param productId the product identifier supplied by the caller
     * @param quantity the requested quantity
     */
    public record Item(String productId, int quantity) {}
}

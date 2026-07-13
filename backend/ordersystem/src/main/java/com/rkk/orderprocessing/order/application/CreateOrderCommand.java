package com.rkk.orderprocessing.order.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistence-free input for creating an order.
 *
 * <p>The command intentionally permits invalid values so the application service can apply the
 * same invariants to HTTP and non-HTTP callers and report a controlled validation failure.</p>
 */
public record CreateOrderCommand(List<Item> items) {

    /** Creates a defensive snapshot of the caller's item list. */
    public CreateOrderCommand {
        items = items == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(items));
    }

    /** Immutable product and quantity input for one submitted item. */
    public record Item(String productId, int quantity) {}
}

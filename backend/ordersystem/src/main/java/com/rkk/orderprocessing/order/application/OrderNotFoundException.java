package com.rkk.orderprocessing.order.application;

import java.util.UUID;

/** Signals that a syntactically valid order identifier is absent. */
public final class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }
}

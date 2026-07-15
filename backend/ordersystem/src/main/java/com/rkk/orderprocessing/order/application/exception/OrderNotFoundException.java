package com.rkk.orderprocessing.order.application.exception;

import java.util.UUID;

/** Raised when no order exists for a valid ID. */
public final class OrderNotFoundException extends RuntimeException {

    /**
     * Creates a not-found error for one order.
     *
     * @param orderId the ID that was not found
     */
    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }
}

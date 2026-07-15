package com.rkk.orderprocessing.order.application.exception;

import java.util.UUID;

/** Raised when a status change is not allowed or loses a race with another update. */
public final class OrderStateConflictException extends RuntimeException {

    /**
     * Creates a conflict error for one order.
     *
     * @param orderId the order whose status could not be changed
     */
    public OrderStateConflictException(UUID orderId) {
        super("Order state conflict: " + orderId);
    }
}

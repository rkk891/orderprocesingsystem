package com.rkk.orderprocessing.order.application;

import java.util.UUID;

/** Signals a rejected lifecycle mutation, including a lost conditional-update race. */
public final class OrderStateConflictException extends RuntimeException {

    public OrderStateConflictException(UUID orderId) {
        super("Order state conflict: " + orderId);
    }
}

package com.rkk.orderprocessing.order.domain;

import java.util.Optional;

/** Order statuses and the previous status required for each manual status change. */
public enum OrderStatus {
    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /**
     * Returns the status an order must currently have before moving to this status manually.
     * {@code PENDING} and {@code CANCELLED} are not valid manual targets.
     *
     * @return the required previous status, or an empty value when this status cannot be requested manually
     */
    public Optional<OrderStatus> requiredPredecessorForManualTarget() {
        return switch (this) {
            case PROCESSING -> Optional.of(PENDING);
            case SHIPPED -> Optional.of(PROCESSING);
            case DELIVERED -> Optional.of(SHIPPED);
            case PENDING, CANCELLED -> Optional.empty();
        };
    }
}

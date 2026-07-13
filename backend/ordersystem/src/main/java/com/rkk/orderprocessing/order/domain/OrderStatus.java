package com.rkk.orderprocessing.order.domain;

import java.util.Optional;

/**
 * Order lifecycle policy shared by the application and persistence layers.
 */
public enum OrderStatus {
    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /**
     * Returns the single required predecessor for a manual fulfilment target.
     * {@code PENDING} and {@code CANCELLED} are not valid manual targets.
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

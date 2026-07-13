package com.rkk.orderprocessing.order.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void allowsOnlyAdjacentManualFulfilmentTransitions() {
        for (OrderStatus source : OrderStatus.values()) {
            for (OrderStatus target : OrderStatus.values()) {
                boolean expected = (source == OrderStatus.PENDING && target == OrderStatus.PROCESSING)
                        || (source == OrderStatus.PROCESSING && target == OrderStatus.SHIPPED)
                        || (source == OrderStatus.SHIPPED && target == OrderStatus.DELIVERED);

                boolean actual = target.requiredPredecessorForManualTarget()
                        .filter(source::equals)
                        .isPresent();
                assertThat(actual)
                        .as("%s -> %s", source, target)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void exposesTheRequiredPredecessorOnlyForManualTargets() {
        assertThat(OrderStatus.PENDING.requiredPredecessorForManualTarget()).isEmpty();
        assertThat(OrderStatus.PROCESSING.requiredPredecessorForManualTarget())
                .contains(OrderStatus.PENDING);
        assertThat(OrderStatus.SHIPPED.requiredPredecessorForManualTarget())
                .contains(OrderStatus.PROCESSING);
        assertThat(OrderStatus.DELIVERED.requiredPredecessorForManualTarget())
                .contains(OrderStatus.SHIPPED);
        assertThat(OrderStatus.CANCELLED.requiredPredecessorForManualTarget()).isEmpty();
    }

}

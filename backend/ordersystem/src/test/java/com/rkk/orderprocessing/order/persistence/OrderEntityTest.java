package com.rkk.orderprocessing.order.persistence;

import com.rkk.orderprocessing.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderEntityTest {

    @Test
    void createsPendingAggregateWithOneTimestampAndParentLinks() {
        UUID orderId = UUID.fromString("1ddb98c5-9a28-4ad4-a368-8a66db5f5d52");
        Instant timestamp = Instant.parse("2026-07-13T08:15:30Z");
        OrderItemEntity first = OrderItemEntity.create(0, "SKU-001", 2);
        OrderItemEntity second = OrderItemEntity.create(1, "SKU-002", 4);

        OrderEntity order = OrderEntity.createPending(orderId, timestamp, List.of(first, second));

        assertThat(order.getId()).isEqualTo(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCreatedAt()).isEqualTo(timestamp);
        assertThat(order.getUpdatedAt()).isEqualTo(timestamp);
        assertThat(order.getItems()).containsExactly(first, second);
        assertThat(first.getOrder()).isSameAs(order);
        assertThat(second.getOrder()).isSameAs(order);
    }

    @Test
    void preservesExplicitItemPositionsAndValues() {
        OrderItemEntity item = OrderItemEntity.create(7, "opaque-product", 999);

        assertThat(item.getPosition()).isEqualTo(7);
        assertThat(item.getProductId()).isEqualTo("opaque-product");
        assertThat(item.getQuantity()).isEqualTo(999);
    }

    @Test
    void doesNotExposeAMutableItemCollection() {
        OrderEntity order = OrderEntity.createPending(
                UUID.randomUUID(),
                Instant.parse("2026-07-13T08:15:30Z"),
                List.of(OrderItemEntity.create(0, "SKU-001", 1))
        );

        assertThatThrownBy(() -> order.getItems().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsItemCardinalityOutsideTheAggregateBoundary() {
        Instant timestamp = Instant.parse("2026-07-13T08:15:30Z");
        List<OrderItemEntity> tooMany = items(101);

        assertThatThrownBy(() -> OrderEntity.createPending(
                UUID.randomUUID(), timestamp, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("items must contain between 1 and 100 items");
        assertThatThrownBy(() -> OrderEntity.createPending(
                UUID.randomUUID(), timestamp, tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("items must contain between 1 and 100 items");
        assertThat(tooMany).allMatch(item -> item.getOrder() == null);
    }

    @Test
    void acceptsTheMaximumItemCardinalityAndAttachesEveryItem() {
        List<OrderItemEntity> maximumItems = items(100);

        OrderEntity order = OrderEntity.createPending(
                UUID.randomUUID(),
                Instant.parse("2026-07-13T08:15:30Z"),
                maximumItems);

        assertThat(order.getItems()).hasSize(100);
        assertThat(maximumItems).allMatch(item -> item.getOrder() == order);
    }

    @Test
    void rejectsReattachingAnItemToAnotherAggregate() {
        OrderItemEntity item = OrderItemEntity.create(0, "SKU-001", 1);
        OrderEntity.createPending(
                UUID.randomUUID(),
                Instant.parse("2026-07-13T08:15:30Z"),
                List.of(item)
        );

        assertThatThrownBy(() -> OrderEntity.createPending(
                UUID.randomUUID(),
                Instant.parse("2026-07-13T08:15:31Z"),
                List.of(item)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Order item is already attached to another order");
    }

    @Test
    void validatesEveryItemBeforeAttachingAnyToAFailedAggregate() {
        OrderItemEntity fresh = OrderItemEntity.create(0, "SKU-FRESH", 1);
        OrderItemEntity alreadyAttached = OrderItemEntity.create(1, "SKU-ATTACHED", 1);
        OrderEntity original = OrderEntity.createPending(
                UUID.randomUUID(),
                Instant.parse("2026-07-13T08:15:30Z"),
                List.of(alreadyAttached));

        assertThatThrownBy(() -> OrderEntity.createPending(
                UUID.randomUUID(),
                Instant.parse("2026-07-13T08:15:31Z"),
                List.of(fresh, alreadyAttached)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fresh.getOrder()).isNull();
        assertThat(alreadyAttached.getOrder()).isSameAs(original);
    }

    private static List<OrderItemEntity> items(int count) {
        return IntStream.range(0, count)
                .mapToObj(position -> OrderItemEntity.create(position, "SKU-" + position, 1))
                .toList();
    }
}

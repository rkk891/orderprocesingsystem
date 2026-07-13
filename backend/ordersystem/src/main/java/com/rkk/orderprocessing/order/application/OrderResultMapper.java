package com.rkk.orderprocessing.order.application;

import com.rkk.orderprocessing.order.persistence.OrderEntity;
import com.rkk.orderprocessing.order.persistence.OrderItemEntity;
import com.rkk.orderprocessing.order.persistence.OrderRepository.OrderSummaryProjection;
import java.util.Comparator;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Detaches JPA aggregates and projections into immutable application results while a transaction
 * is open.
 */
@Component
public class OrderResultMapper {

    /** Maps a fully loaded aggregate and preserves the submitted item order. */
    public OrderDetailsResult toDetails(OrderEntity entity) {
        var items = entity.getItems().stream()
                .sorted(Comparator.comparingInt(OrderItemEntity::getPosition))
                .map(item -> new OrderDetailsResult.Item(item.getProductId(), item.getQuantity()))
                .toList();

        return new OrderDetailsResult(
                entity.getId(),
                entity.getStatus().name(),
                items,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /** Maps a database summary page without loading any item collection. */
    public OrderPageResult toPage(Page<OrderSummaryProjection> page) {
        var summaries = page.getContent().stream()
                .map(summary -> new OrderPageResult.Summary(
                        summary.getId(),
                        summary.getStatus().name(),
                        summary.getItemCount(),
                        summary.getCreatedAt(),
                        summary.getUpdatedAt()))
                .toList();

        return new OrderPageResult(
                summaries,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}

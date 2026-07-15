package com.rkk.orderprocessing.order.application;

import com.rkk.orderprocessing.order.application.result.OrderDetails;
import com.rkk.orderprocessing.order.application.result.OrderPage;
import com.rkk.orderprocessing.order.persistence.OrderEntity;
import com.rkk.orderprocessing.order.persistence.OrderItemEntity;
import com.rkk.orderprocessing.order.persistence.OrderRepository.OrderSummaryProjection;
import java.util.Comparator;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Converts database-backed order data into application results while the transaction is open.
 *
 * <p>JPA entities stay inside the application and persistence layers. Copying their values here
 * prevents the controller from reading lazy database state after the transaction closes and keeps
 * the API layer independent from JPA.</p>
 */
@Component
public class DataMapper {

    /**
     * Builds the complete order result used by the API layer.
     * Items are sorted by their stored position so the response keeps the order submitted by the
     * client.
     *
     * @param entity the order entity loaded with all of its items
     * @return an immutable result containing the order and its items
     */
    public OrderDetails toDetails(OrderEntity entity) {
        var items = entity.getItems().stream()
                .sorted(Comparator.comparingInt(OrderItemEntity::getPosition))
                .map(item -> new OrderDetails.Item(item.getProductId(), item.getQuantity()))
                .toList();

        return new OrderDetails(
                entity.getId(),
                entity.getStatus().name(),
                items,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /**
     * Builds a page of order summaries from the lightweight database query.
     * This path copies only summary fields and does not load every line item for every order.
     *
     * @param page the summary rows and page information returned by the repository
     * @return an immutable result containing the summaries and page information
     */
    public OrderPage toPage(Page<OrderSummaryProjection> page) {
        var summaries = page.getContent().stream()
                .map(summary -> new OrderPage.Summary(
                        summary.getId(),
                        summary.getStatus().name(),
                        summary.getItemCount(),
                        summary.getCreatedAt(),
                        summary.getUpdatedAt()))
                .toList();

        return new OrderPage(
                summaries,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}

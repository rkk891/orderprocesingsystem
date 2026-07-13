package com.rkk.orderprocessing.order.api;

import com.rkk.orderprocessing.order.api.dto.CreateOrderRequest;
import com.rkk.orderprocessing.order.api.dto.OrderItemResponse;
import com.rkk.orderprocessing.order.api.dto.OrderPageResponse;
import com.rkk.orderprocessing.order.api.dto.OrderResponse;
import com.rkk.orderprocessing.order.api.dto.OrderSummaryResponse;
import com.rkk.orderprocessing.order.application.CreateOrderCommand;
import com.rkk.orderprocessing.order.application.OrderDetailsResult;
import com.rkk.orderprocessing.order.application.OrderPageResult;
import org.springframework.stereotype.Component;

/** Maps immutable HTTP records to and from the persistence-free application boundary. */
@Component
public class OrderApiMapper {

    /** Maps validated HTTP input without importing a domain or persistence type. */
    public CreateOrderCommand toCommand(CreateOrderRequest request) {
        var items = request.items().stream()
                .map(item -> new CreateOrderCommand.Item(item.productId(), item.quantity()))
                .toList();
        return new CreateOrderCommand(items);
    }

    /** Maps detached detail data to the public JSON shape. */
    public OrderResponse toResponse(OrderDetailsResult result) {
        var items = result.items().stream()
                .map(item -> new OrderItemResponse(item.productId(), item.quantity()))
                .toList();
        return new OrderResponse(
                result.id(),
                result.status(),
                items,
                result.createdAt(),
                result.updatedAt());
    }

    /** Maps detached summary data and explicit page metadata to the public JSON shape. */
    public OrderPageResponse toResponse(OrderPageResult result) {
        var content = result.content().stream()
                .map(summary -> new OrderSummaryResponse(
                        summary.id(),
                        summary.status(),
                        summary.itemCount(),
                        summary.createdAt(),
                        summary.updatedAt()))
                .toList();
        return new OrderPageResponse(
                content,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.first(),
                result.last());
    }
}

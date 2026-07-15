package com.rkk.orderprocessing.order.api;

import com.rkk.orderprocessing.order.api.request.CreateOrderRequest;
import com.rkk.orderprocessing.order.api.response.OrderItemResponse;
import com.rkk.orderprocessing.order.api.response.OrderPageResponse;
import com.rkk.orderprocessing.order.api.response.OrderResponse;
import com.rkk.orderprocessing.order.api.response.OrderSummaryResponse;
import com.rkk.orderprocessing.order.application.command.CreateOrderCommand;
import com.rkk.orderprocessing.order.application.result.OrderDetailsResult;
import com.rkk.orderprocessing.order.application.result.OrderPageResult;
import org.springframework.stereotype.Component;

/**
 * Converts between API objects and application objects.
 * This keeps HTTP-specific request and response classes out of {@code OrderService}.
 */
@Component
public class OrderApiMapper {

    /**
     * Converts validated HTTP input into the command used by {@code OrderService}.
     *
     * @param request the create-order request received by the controller
     * @return a command containing the same products and quantities
     */
    public CreateOrderCommand toCommand(CreateOrderRequest request) {
        var items = request.items().stream()
                .map(item -> new CreateOrderCommand.Item(item.productId(), item.quantity()))
                .toList();
        return new CreateOrderCommand(items);
    }

    /**
     * Converts complete order data from the service into the public API response.
     *
     * @param result the complete order returned by the service
     * @return the response sent to the API client
     */
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

    /**
     * Converts a page of order summaries into the public list response.
     *
     * @param result the page returned by the service
     * @return the response containing summaries and page information
     */
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

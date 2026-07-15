package com.rkk.orderprocessing.order.api;

import com.rkk.orderprocessing.order.api.request.NewOrderRequest;
import com.rkk.orderprocessing.order.api.response.PageResponse;
import com.rkk.orderprocessing.order.api.response.OrderResponse;
import com.rkk.orderprocessing.order.api.response.SummaryResponse;
import com.rkk.orderprocessing.order.application.command.CreateOrderData;
import com.rkk.orderprocessing.order.application.result.OrderDetails;
import com.rkk.orderprocessing.order.application.result.OrderPage;
import org.springframework.stereotype.Component;

/**
 * Converts between API objects and application objects.
 * This keeps HTTP-specific request and response classes out of {@code OrderService}.
 */
@Component
public class ApiMapper {

    /**
     * Converts validated HTTP input into the command used by {@code OrderService}.
     *
     * @param request the create-order request received by the controller
     * @return a data object containing the same products and quantities
     */
    public CreateOrderData toData(NewOrderRequest request) {
        var items = request.items().stream()
                .map(item -> new CreateOrderData.Item(item.productId(), item.quantity()))
                .toList();
        return new CreateOrderData(items);
    }

    /**
     * Converts complete order data from the service into the public API response.
     *
     * @param result the complete order returned by the service
     * @return the response sent to the API client
     */
    public OrderResponse toResponse(OrderDetails result) {
        var items = result.items().stream()
                .map(item -> new OrderResponse.Item(item.productId(), item.quantity()))
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
    public PageResponse toResponse(OrderPage result) {
        var content = result.content().stream()
                .map(summary -> new SummaryResponse(
                        summary.id(),
                        summary.status(),
                        summary.itemCount(),
                        summary.createdAt(),
                        summary.updatedAt()))
                .toList();

        return new PageResponse(
                content,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.first(),
                result.last());
    }
}

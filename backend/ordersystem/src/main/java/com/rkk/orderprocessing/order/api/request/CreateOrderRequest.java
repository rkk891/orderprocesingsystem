package com.rkk.orderprocessing.order.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HTTP request body accepted by the create-order endpoint.
 * Spring validates that the item list is present and contains between 1 and 100 entries before the
 * controller creates the order.
 *
 * @param items the list of order items to create.
 */
public record CreateOrderRequest(
        @NotNull(message = "must be provided")
        @Size(min = 1, max = 100, message = "must contain between 1 and 100 items")
        List<@Valid @NotNull(message = "must not contain null items") CreateOrderItemRequest> items) {

    /**
     * Copies the list so code holding the original list cannot change the request after validation.
     * A null value is kept because Spring validation must report it as an input error.
     */
    public CreateOrderRequest {
        items = items == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(items));
    }
}

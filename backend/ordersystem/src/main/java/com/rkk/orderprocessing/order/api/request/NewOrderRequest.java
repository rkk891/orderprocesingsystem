package com.rkk.orderprocessing.order.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
 * External representation of an order creation request.
 *
 * <p><b>Annotation Mechanics:</b>
 * <ul>
 *   <li>{@code @NotNull}, {@code @Size}, {@code @Min}, {@code @NotBlank}: These are Jakarta Validation
 *   constraints. When the controller method uses {@code @Valid}, Spring's validator reflects over
 *   these fields. If any incoming payload violates these rules, a {@code MethodArgumentNotValidException}
 *   is thrown, guaranteeing that the application logic only ever deals with structurally sound data.</li>
 * </ul>
 *
 * @param items the list of order items to create.
 */
public record NewOrderRequest(
        @NotNull(message = "must be provided")
        @Size(min = 1, max = 100, message = "must contain between 1 and 100 items")
        List<@Valid @NotNull(message = "must not contain null items") Item> items) {

    /**
     * Copies the list so code holding the original list cannot change the request after validation.
     * A null value is kept because Spring validation must report it as an input error.
     */

    /**
     * Product and quantity supplied for one item in a create-order request.
     */
    public record Item(
            @NotNull(message = "must be provided")
            @NotBlank(message = "must not be blank")
            String productId,
            @NotNull(message = "must be provided")
            @Min(value = 1, message = "must be at least 1")
            @Max(value = 999, message = "must be at most 999")
            Integer quantity) {}

    public NewOrderRequest {
        items = items == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(items));
    }
}

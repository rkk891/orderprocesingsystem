package com.rkk.orderprocessing.order.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * HTTP request body for a manual order-status change.
 * The application service converts the text to {@code OrderStatus} and checks whether the move is
 * allowed from the order's saved status.
 *
 * @param status the requested target status string.
 */
public record UpdateOrderStatusRequest(
        @NotNull(message = "must be provided")
        @NotBlank(message = "must not be blank")
        String status) {}

package com.rkk.orderprocessing.order.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * HTTP request body for a manual order-status change.
 * The application service converts the text to {@code OrderStatus} and checks whether the move is
 * allowed from the order's saved status.
 *
 * <p><b>Annotation Mechanics:</b>
 * <ul>
 *   <li>{@code @NotNull}, {@code @NotBlank}: These are Jakarta Validation constraints that Spring
 *   enforces automatically via the controller's {@code @Valid} annotation. This stops malformed
 *   requests at the API boundary, keeping the core domain logic clean.</li>
 * </ul>
 *
 * @param status the requested target status string.
 */
public record UpdateStatusRequest(
        @NotNull(message = "must be provided")
        @NotBlank(message = "must not be blank")
        String status) {}

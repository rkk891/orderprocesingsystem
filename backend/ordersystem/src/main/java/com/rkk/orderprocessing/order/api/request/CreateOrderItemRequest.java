package com.rkk.orderprocessing.order.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Product and quantity supplied for one item in a create-order request.
 * The product ID is treated only as an identifier because product-catalog and pricing data are
 * outside this service.
 *
 * @param productId the identifier of the requested product.
 * @param quantity the number of items ordered.
 */
public record CreateOrderItemRequest(
        @NotNull(message = "must be provided")
        @NotBlank(message = "must not be blank")
        String productId,
        @NotNull(message = "must be provided")
        @Min(value = 1, message = "must be at least 1")
        @Max(value = 999, message = "must be at most 999")
        Integer quantity) {}

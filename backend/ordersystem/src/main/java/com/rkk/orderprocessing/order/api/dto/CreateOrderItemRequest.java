package com.rkk.orderprocessing.order.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Strict HTTP input for one immutable order item. */
public record CreateOrderItemRequest(
        @NotNull(message = "must be provided")
        @NotBlank(message = "must not be blank")
        String productId,
        @NotNull(message = "must be provided")
        @Min(value = 1, message = "must be at least 1")
        @Max(value = 999, message = "must be at most 999")
        Integer quantity) {}

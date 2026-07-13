package com.rkk.orderprocessing.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Strict create-order request containing only the documented item array. */
public record CreateOrderRequest(
        @NotNull(message = "must be provided")
        @Size(min = 1, max = 100, message = "must contain between 1 and 100 items")
        List<@Valid @NotNull(message = "must not contain null items") CreateOrderItemRequest> items) {}

package com.rkk.orderprocessing.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Strict status-update request; parsing remains inside the application boundary. */
public record UpdateOrderStatusRequest(
        @NotNull(message = "must be provided")
        @NotBlank(message = "must not be blank")
        String status) {}

package com.rkk.orderprocessing.order.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Compact list representation that deliberately omits item arrays. */
public record OrderSummaryResponse(
        UUID id,
        String status,
        long itemCount,
        Instant createdAt,
        Instant updatedAt) {}

package com.rkk.orderprocessing.order.api.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact list representation that deliberately omits item arrays.
 *
 * @param id the unique identifier of the order.
 * @param status the string representation of the current order status.
 * @param itemCount the number of line items associated with this order.
 * @param createdAt the instant the order was originally placed.
 * @param updatedAt the instant the order was last modified.
 */
public record OrderSummaryResponse(
        UUID id,
        String status,
        long itemCount,
        Instant createdAt,
        Instant updatedAt) {}

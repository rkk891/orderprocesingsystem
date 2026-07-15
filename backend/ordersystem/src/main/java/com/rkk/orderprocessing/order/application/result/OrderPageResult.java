package com.rkk.orderprocessing.order.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One page of order summaries returned by the application.
 *
 * @param content the order summaries on this page
 * @param page the zero-based page number
 * @param size the requested number of orders per page
 * @param totalElements the total number of matching orders
 * @param totalPages the number of available pages
 * @param first whether this is the first page
 * @param last whether this is the last page
 */
public record OrderPageResult(
        List<Summary> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    /** Copies the summaries so the result cannot change after the transaction closes. */
    public OrderPageResult {
        content = List.copyOf(content);
    }

    /**
     * Summary data copied from the repository's database-specific view. Returning this record keeps
     * persistence details out of the controller and API response classes.
     *
     * @param id the order ID
     * @param status the current order status
     * @param itemCount the number of line items in the order
     * @param createdAt when the order was created
     * @param updatedAt when the order was last updated
     */
    public record Summary(
            UUID id,
            String status,
            long itemCount,
            Instant createdAt,
            Instant updatedAt) {}
}

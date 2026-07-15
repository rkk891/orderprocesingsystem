package com.rkk.orderprocessing.order.api.response;

import java.util.List;

/**
 * One page of order summaries plus the information needed to navigate through the other pages.
 *
 * @param content the list of summaries for the current page.
 * @param page the current page index (zero-based).
 * @param size the maximum number of order summaries per page.
 * @param totalElements the total number of matching orders across all pages.
 * @param totalPages the total number of pages available.
 * @param first true if this is the first page.
 * @param last true if this is the last page.
 */
public record OrderPageResponse(
        List<OrderSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    /** Keeps the page immutable after mapping. */
    public OrderPageResponse {
        content = List.copyOf(content);
    }
}

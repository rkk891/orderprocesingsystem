package com.rkk.orderprocessing.order.api.dto;

import java.util.List;

/** Bounded order-summary page with explicit traversal metadata. */
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

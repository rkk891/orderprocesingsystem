package com.rkk.orderprocessing.order.api.response;

/**
 * One immutable item in an order-detail response.
 *
 * @param productId the identifier of the ordered product.
 * @param quantity the amount of this product requested.
 */
public record OrderItemResponse(String productId, int quantity) {}

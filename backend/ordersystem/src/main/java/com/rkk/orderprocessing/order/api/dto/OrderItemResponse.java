package com.rkk.orderprocessing.order.api.dto;

/** One immutable item in an order-detail response. */
public record OrderItemResponse(String productId, int quantity) {}

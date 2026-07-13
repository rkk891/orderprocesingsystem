package com.rkk.orderprocessing.order.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Immutable line-item data belonging to an {@link OrderEntity} aggregate.
 */
@Entity
@Table(name = "order_items")
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private OrderEntity order;

    @Column(name = "position", nullable = false, updatable = false)
    private short position;

    @Column(name = "product_id", nullable = false, updatable = false, length = 100)
    private String productId;

    @Column(name = "quantity", nullable = false, updatable = false)
    private short quantity;

    protected OrderItemEntity() {
        // Required by JPA.
    }

    private OrderItemEntity(int position, String productId, int quantity) {
        this.position = toSmallInt(position, "position");
        this.productId = Objects.requireNonNull(productId, "productId must not be null");
        this.quantity = toSmallInt(quantity, "quantity");
    }

    /**
     * Creates a line item from values already validated at the application boundary.
     */
    public static OrderItemEntity create(int position, String productId, int quantity) {
        return new OrderItemEntity(position, productId, quantity);
    }

    void attachTo(OrderEntity order) {
        if (this.order != null && this.order != order) {
            throw new IllegalStateException("Order item is already attached to another order");
        }
        this.order = Objects.requireNonNull(order, "order must not be null");
    }

    /** Fails before aggregate construction mutates any item when this item already has a parent. */
    void requireUnattached() {
        if (order != null) {
            throw new IllegalStateException("Order item is already attached to another order");
        }
    }

    private static short toSmallInt(int value, String fieldName) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException(fieldName + " does not fit a PostgreSQL smallint");
        }
        return (short) value;
    }

    public Long getId() {
        return id;
    }

    public OrderEntity getOrder() {
        return order;
    }

    public int getPosition() {
        return position;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }
}

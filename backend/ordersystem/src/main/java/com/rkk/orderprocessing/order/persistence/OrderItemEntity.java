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
 * Database representation of one item saved as part of an {@link OrderEntity}.
 * Product and quantity values never change after the item is created.
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
     *
     * @param position the zero-based index of this item in the order list.
     * @param productId the product identifier supplied by the caller.
     * @param quantity the number of items ordered.
     * @return a new item that is not yet linked to an order
     * @throws IllegalArgumentException if quantity or position exceed PostgreSQL smallint limits.
     * @throws NullPointerException if productId is null.
     */
    public static OrderItemEntity create(int position, String productId, int quantity) {
        return new OrderItemEntity(position, productId, quantity);
    }

    /**
     * Links this item to its order before JPA saves both records.
     *
     * @param order the parent OrderEntity.
     * @throws IllegalStateException if the item is already bound to a different order.
     * @throws NullPointerException if order is null.
     */
    void attachTo(OrderEntity order) {
        if (this.order != null && this.order != order) {
            throw new IllegalStateException("Order item is already attached to another order");
        }
        this.order = Objects.requireNonNull(order, "order must not be null");
    }

    /**
     * Checks that the item does not already belong to an order. The order checks every item first,
     * so a failure cannot leave only part of the supplied list attached.
     *
     * @throws IllegalStateException if this item is already attached.
     */
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

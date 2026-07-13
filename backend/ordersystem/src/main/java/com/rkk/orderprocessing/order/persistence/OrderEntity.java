package com.rkk.orderprocessing.order.persistence;

import com.rkk.orderprocessing.order.domain.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistence aggregate for an order and its immutable line items.
 */
@Entity
@Table(name = "orders")
public class OrderEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    @OrderBy("position ASC")
    private List<OrderItemEntity> items = new ArrayList<>();

    @Transient
    private boolean newEntity = true;

    protected OrderEntity() {
        // Required by JPA.
    }

    private OrderEntity(UUID id, Instant timestamp, List<OrderItemEntity> items) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.status = OrderStatus.PENDING;
        this.createdAt = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.updatedAt = timestamp;

        List<OrderItemEntity> validatedItems = List.copyOf(
                Objects.requireNonNull(items, "items must not be null"));
        validatedItems.forEach(OrderItemEntity::requireUnattached);
        validatedItems.forEach(this::addItem);
    }

    /**
     * Creates a pending aggregate using one timestamp for both audit columns.
     */
    public static OrderEntity createPending(
            UUID id,
            Instant timestamp,
            List<OrderItemEntity> items
    ) {
        return new OrderEntity(id, timestamp, items);
    }

    private void addItem(OrderItemEntity item) {
        OrderItemEntity requiredItem = Objects.requireNonNull(item, "items must not contain null");
        requiredItem.attachTo(this);
        items.add(requiredItem);
    }

    /**
     * Ensures Spring Data persists aggregates with application-assigned UUIDs instead of merging them.
     */
    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    private void markNotNew() {
        newEntity = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItemEntity> getItems() {
        return Collections.unmodifiableList(items);
    }
}

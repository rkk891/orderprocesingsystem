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
 * Database representation of one order and the items saved with it.
 * Saving a new order also saves its items, while later status changes are handled by guarded
 * repository updates.
 */
@Entity
@Table(name = "orders")
public class OrderEntity implements Persistable<UUID> {

    private static final int MIN_ITEMS = 1;
    private static final int MAX_ITEMS = 100;

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
        if (validatedItems.size() < MIN_ITEMS || validatedItems.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("items must contain between 1 and 100 items");
        }
        validatedItems.forEach(OrderItemEntity::requireUnattached);
        validatedItems.forEach(this::addItem);
    }

    /**
     * Creates a new pending order and attaches all validated items to it. The same timestamp is
     * used for {@code createdAt} and {@code updatedAt} because no later change has happened yet.
     *
     * @param id the globally unique identifier for this order.
     * @param timestamp the exact instant of creation.
     * @param items the list of line items belonging to this order.
     * @return a complete order ready to be inserted
     * @throws IllegalArgumentException if the list does not contain between 1 and 100 items
     * @throws IllegalStateException if an item already belongs to another order
     * @throws NullPointerException if an argument or item is null
     */
    public static OrderEntity createPending(
            UUID id,
            Instant timestamp,
            List<OrderItemEntity> items
    ) {
        return new OrderEntity(id, timestamp, items);
    }

    /**
     * Adds an item to the order and also sets the item's order reference. JPA needs both sides of
     * this relationship to point to each other before saving.
     *
     * @param item the item to add.
     * @throws NullPointerException if the item is null.
     */
    private void addItem(OrderItemEntity item) {
        OrderItemEntity requiredItem = Objects.requireNonNull(item, "items must not contain null");
        requiredItem.attachTo(this);
        items.add(requiredItem);
    }

    /**
     * Tells Spring Data whether this object needs an SQL insert. IDs are created by the application,
     * so a non-null UUID alone cannot tell Spring whether the row is new.
     *
     * @return true if the entity is newly created and should be inserted; false otherwise.
     */
    @Override
    public boolean isNew() {
        return newEntity;
    }

    /**
     * Clears the new-order flag after JPA inserts or loads the row, so later saves are treated as
     * updates rather than new inserts.
     */
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

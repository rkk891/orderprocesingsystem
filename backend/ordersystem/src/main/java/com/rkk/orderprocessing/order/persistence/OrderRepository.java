package com.rkk.orderprocessing.order.persistence;

import com.rkk.orderprocessing.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes orders in PostgreSQL through Spring Data JPA.
 * Besides normal saves and lookups, it contains the custom queries needed to load order items,
 * return lightweight list rows, and change statuses safely during concurrent requests.
 */
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    /**
     * Loads one order and all of its items in the same query. The application mapper can therefore
     * copy the complete order while the transaction is open, without triggering another database
     * read later from the controller.
     *
     * @param id the UUID of the target order to fetch.
     * @return the order with its items, or an empty value when the ID does not exist
     */
    @Query("""
            select distinct orderEntity
            from OrderEntity orderEntity
            left join fetch orderEntity.items
            where orderEntity.id = :id
            """)
    Optional<OrderEntity> findDetailById(@Param("id") UUID id);

    /**
     * Returns stable newest-first summaries without fetching line items.
     * The summary query returns only the fields needed by the list API, so it does not load every
     * line item merely to show an order in a page.
     *
     * @param pageable specifications for pagination bounds.
     * @return a page of OrderSummaryProjection containing lightweight scalar values.
     */
    @Query(
            value = """
                    select orderEntity.id as id,
                           orderEntity.status as status,
                           size(orderEntity.items) as itemCount,
                           orderEntity.createdAt as createdAt,
                           orderEntity.updatedAt as updatedAt
                    from OrderEntity orderEntity
                    order by orderEntity.createdAt desc, orderEntity.id desc
                    """,
            countQuery = "select count(orderEntity) from OrderEntity orderEntity"
    )
    Page<OrderSummaryProjection> findSummaryPage(Pageable pageable);

    /**
     * Returns stable newest-first summaries for one exact status.
     *
     * @param status the target OrderStatus enum value to filter by.
     * @param pageable specifications for pagination bounds.
     * @return a page of OrderSummaryProjection filtered to matching rows.
     */
    @Query(
            value = """
                    select orderEntity.id as id,
                           orderEntity.status as status,
                           size(orderEntity.items) as itemCount,
                           orderEntity.createdAt as createdAt,
                           orderEntity.updatedAt as updatedAt
                    from OrderEntity orderEntity
                    where orderEntity.status = :status
                    order by orderEntity.createdAt desc, orderEntity.id desc
                    """,
            countQuery = """
                    select count(orderEntity)
                    from OrderEntity orderEntity
                    where orderEntity.status = :status
                    """
    )
    Page<OrderSummaryProjection> findSummaryPageByStatus(
            @Param("status") OrderStatus status,
            Pageable pageable
    );

    /**
     * Atomically changes one order only when its current state matches the caller's expectation.
     * The order ID and expected status are checked in the same SQL update. If another request
     * changes the status first, this update matches no row and returns {@code 0}; the application
     * service then reports a state conflict instead of overwriting the newer status.
     *
     * @param id the target order identifier.
     * @param expectedStatus the status that must currently be saved.
     * @param targetStatus the new status to apply.
     * @param clockInstant the exact point-in-time to write as the new updated_at value.
     * @return the number of rows affected (should be exactly 1 for a successful transition).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update orders
            set status = :#{#targetStatus.name()},
                updated_at = greatest(updated_at, :clockInstant)
            where id = :id
              and status = :#{#expectedStatus.name()}
            """, nativeQuery = true)
    int updateStatusIfExpected(
            @Param("id") UUID id,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("targetStatus") OrderStatus targetStatus,
            @Param("clockInstant") Instant clockInstant
    );

    /**
     * Changes every row still {@code PENDING} to {@code PROCESSING} in one SQL update. A repeated
     * or overlapping job run cannot update the same order again because its saved status is no
     * longer {@code PENDING}.
     *
     * @param clockInstant the timestamp to apply for updated rows.
     * @return the total number of rows migrated from PENDING to PROCESSING.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update orders
            set status = 'PROCESSING',
                updated_at = greatest(updated_at, :clockInstant)
            where status = 'PENDING'
            """, nativeQuery = true)
    int processPending(@Param("clockInstant") Instant clockInstant);

    /**
     * Small read-only view used by the list query. It contains only summary columns, so listing
     * orders does not create full {@link OrderEntity} objects or load their item collections.
     */
    interface OrderSummaryProjection {

        /** @return the order ID. */
        UUID getId();

        /** @return the order's current status. */
        OrderStatus getStatus();

        /** @return the count of associated items derived via JPA size() function. */
        long getItemCount();

        /** @return the original creation timestamp. */
        Instant getCreatedAt();

        /** @return the most recent update timestamp. */
        Instant getUpdatedAt();
    }
}

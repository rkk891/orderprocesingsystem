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
 * Order persistence contract, including aggregate reads and atomic status mutations.
 */
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    /**
     * Loads one complete aggregate without relying on an open persistence context later.
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
     * Promotes every row still pending in one idempotent set-based statement.
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
     * Projection used by pageable list reads so item collections are never loaded.
     */
    interface OrderSummaryProjection {

        UUID getId();

        OrderStatus getStatus();

        long getItemCount();

        Instant getCreatedAt();

        Instant getUpdatedAt();
    }
}

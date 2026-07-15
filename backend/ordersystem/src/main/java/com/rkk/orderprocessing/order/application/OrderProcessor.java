package com.rkk.orderprocessing.order.application;

import com.rkk.orderprocessing.order.persistence.OrderRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the scheduled {@code PENDING -> PROCESSING} update.
 *
 * <p>The scheduler only decides when to run. This service owns the transaction and database work,
 * which also makes the same operation easy to call directly from tests.</p>
 *
 * <p><b>Annotation Mechanics:</b>
 * <ul>
 *   <li>{@code @Transactional}: Spring intercepts method calls to this class, opening a database
 *   transaction before the method executes. It commits the transaction if the method returns normally,
 *   and automatically rolls it back if a {@code RuntimeException} is thrown, preventing partial updates.</li>
 * </ul>
 */
@Service
public class OrderProcessor {

    private final OrderRepository repository;
    private final Clock clock;

    /**
     * Creates the processor with its database access and time source.
     *
     * @param repository reads and updates saved orders
     * @param clock supplies the timestamp written to updated orders
     */
    public OrderProcessor(OrderRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Changes every order that is still {@code PENDING} to {@code PROCESSING} using one SQL update
     * and one transaction. PostgreSQL checks the saved status while running the update. If two job
     * runs overlap, the first update changes the row and the second no longer finds it pending, so
     * the same order is not processed twice.
     *
     * @return the number of orders changed from {@code PENDING} to {@code PROCESSING}
     */
    @Transactional
    public int processPending() {
        return repository.processPending(clock.instant());
    }
}

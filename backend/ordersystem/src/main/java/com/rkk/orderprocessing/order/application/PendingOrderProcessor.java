package com.rkk.orderprocessing.order.application;

import com.rkk.orderprocessing.order.persistence.OrderRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional set-based processor invoked by the thin scheduling adapter. */
@Service
public class PendingOrderProcessor {

    private final OrderRepository repository;
    private final Clock clock;

    public PendingOrderProcessor(OrderRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Promotes every row still pending using one timestamp and returns the committed candidate
     * count to the caller. Repeated or overlapping executions are safe because the repository
     * predicate is evaluated by PostgreSQL.
     */
    @Transactional
    public int processPending() {
        return repository.processPending(clock.instant());
    }
}

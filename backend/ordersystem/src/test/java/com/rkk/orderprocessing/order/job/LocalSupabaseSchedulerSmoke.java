package com.rkk.orderprocessing.order.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.rkk.orderprocessing.order.application.OrderProcessor;
import com.rkk.orderprocessing.order.domain.OrderStatus;
import com.rkk.orderprocessing.order.persistence.OrderEntity;
import com.rkk.orderprocessing.order.persistence.OrderItemEntity;
import com.rkk.orderprocessing.order.persistence.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Explicit opt-in smoke for one scheduler handler run against local Supabase.
 *
 * <p>The class name deliberately does not match the normal Surefire patterns. Run it only with
 * {@code -Dtest=LocalSupabaseSchedulerSmoke} and externally supplied local datasource variables.</p>
 */
@SpringBootTest(properties = "orders.scheduler.enabled=false")
class LocalSupabaseSchedulerSmoke {

    private static final Instant CREATED_AT = Instant.parse("2026-07-13T12:00:00Z");

    @Autowired
    private OrderProcessor processor;

    @Autowired
    private OrderRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void processesAPendingOrderAndRecordsSuccessfulTelemetry() {
        UUID orderId = UUID.randomUUID();
        repository.saveAndFlush(OrderEntity.createPending(
                orderId,
                CREATED_AT,
                List.of(OrderItemEntity.create(0, "LOCAL-SCHEDULER-SMOKE", 1))));
        SimpleMeterRegistry meters = new SimpleMeterRegistry();

        try {
            new OrderScheduler(processor, meters).processPendingOrders();

            assertThat(repository.findDetailById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PROCESSING);
            assertThat(meters.get("orders.pending.processing.rows").summary().totalAmount())
                    .isGreaterThanOrEqualTo(1);
            assertThat(meters.get("orders.pending.processing.failures").counter().count())
                    .isZero();
        } finally {
            jdbcTemplate.update("delete from orders where id = ?", orderId);
        }
    }
}

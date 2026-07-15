package com.rkk.orderprocessing.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rkk.orderprocessing.order.domain.OrderStatus;
import com.rkk.orderprocessing.order.persistence.OrderEntity;
import com.rkk.orderprocessing.order.persistence.OrderItemEntity;
import com.rkk.orderprocessing.order.persistence.OrderRepository;
import com.rkk.orderprocessing.testsupport.PostgresTestConfiguration;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Direct PostgreSQL integration proof for the transactional pending-order handler. */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresTestConfiguration.class, OrderProcessorIT.BackwardClockConfiguration.class})
class OrderProcessorIT {

    private static final Instant INITIAL_UPDATED_AT = Instant.parse("2026-07-13T10:00:00Z");
    private static final Instant STATUS_UPDATED_AT = Instant.parse("2026-07-13T11:00:00Z");
    private static final Instant BACKWARD_PROCESSOR_TIME = Instant.parse("2026-07-13T09:00:00Z");

    @Autowired
    private OrderProcessor processor;

    @Autowired
    private OrderRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Scenario scenario;

    /** Builds and commits the complete status mix before the processor transaction starts. */
    @BeforeEach
    void setUpCommittedRows() {
        jdbcTemplate.update("delete from orders");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        scenario = transaction.execute(status -> createScenario());
        assertThat(scenario).isNotNull();
    }

    @AfterEach
    void clearRows() {
        jdbcTemplate.update("delete from orders");
    }

    @Test
    void promotesEveryCommittedPendingRowAndLeavesAllOtherStatesUnchanged() {
        assertThat(processor.processPending()).isEqualTo(scenario.pendingIds().size());

        Map<UUID, PersistedState> afterFirstRun = readStates();
        assertThat(scenario.pendingIds()).allSatisfy(orderId -> {
            PersistedState state = afterFirstRun.get(orderId);
            assertThat(state.status()).isEqualTo(OrderStatus.PROCESSING);
            assertThat(state.updatedAt()).isEqualTo(INITIAL_UPDATED_AT);
        });
        scenario.nonPendingStatuses().forEach((orderId, expectedStatus) -> {
            PersistedState state = afterFirstRun.get(orderId);
            assertThat(state.status()).isEqualTo(expectedStatus);
            assertThat(state.updatedAt()).isEqualTo(STATUS_UPDATED_AT);
        });

        assertThat(processor.processPending()).isZero();
        assertThat(readStates()).isEqualTo(afterFirstRun);
    }

    private Scenario createScenario() {
        UUID firstPending = savePending("PROCESSOR-PENDING-1");
        UUID secondPending = savePending("PROCESSOR-PENDING-2");
        UUID processing = savePending("PROCESSOR-PROCESSING");
        UUID shipped = savePending("PROCESSOR-SHIPPED");
        UUID delivered = savePending("PROCESSOR-DELIVERED");
        UUID cancelled = savePending("PROCESSOR-CANCELLED");

        transition(processing, OrderStatus.PENDING, OrderStatus.PROCESSING);
        transition(shipped, OrderStatus.PENDING, OrderStatus.PROCESSING);
        transition(shipped, OrderStatus.PROCESSING, OrderStatus.SHIPPED);
        transition(delivered, OrderStatus.PENDING, OrderStatus.PROCESSING);
        transition(delivered, OrderStatus.PROCESSING, OrderStatus.SHIPPED);
        transition(delivered, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
        transition(cancelled, OrderStatus.PENDING, OrderStatus.CANCELLED);

        Map<UUID, OrderStatus> nonPending = new LinkedHashMap<>();
        nonPending.put(processing, OrderStatus.PROCESSING);
        nonPending.put(shipped, OrderStatus.SHIPPED);
        nonPending.put(delivered, OrderStatus.DELIVERED);
        nonPending.put(cancelled, OrderStatus.CANCELLED);
        return new Scenario(List.of(firstPending, secondPending), Map.copyOf(nonPending));
    }

    private UUID savePending(String productId) {
        UUID orderId = UUID.randomUUID();
        repository.saveAndFlush(OrderEntity.createPending(
                orderId,
                INITIAL_UPDATED_AT,
                List.of(OrderItemEntity.create(0, productId, 1))));
        return orderId;
    }

    private void transition(UUID orderId, OrderStatus expected, OrderStatus target) {
        assertThat(repository.updateStatusIfExpected(
                orderId,
                expected,
                target,
                STATUS_UPDATED_AT)).isOne();
    }

    private Map<UUID, PersistedState> readStates() {
        return jdbcTemplate.query(
                "select id, status, updated_at from orders order by id",
                resultSet -> {
                    Map<UUID, PersistedState> states = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        UUID orderId = resultSet.getObject("id", UUID.class);
                        states.put(orderId, persistedState(resultSet));
                    }
                    return states;
                });
    }

    private static PersistedState persistedState(ResultSet resultSet) throws SQLException {
        return new PersistedState(
                OrderStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private record Scenario(
            List<UUID> pendingIds,
            Map<UUID, OrderStatus> nonPendingStatuses) {}

    private record PersistedState(OrderStatus status, Instant updatedAt) {}

    /** Replaces the production clock only for this context to prove monotonic timestamp handling. */
    @TestConfiguration(proxyBeanMethods = false)
    static class BackwardClockConfiguration {

        @Bean
        @Primary
        Clock backwardProcessorClock() {
            return Clock.fixed(BACKWARD_PROCESSOR_TIME, ZoneOffset.UTC);
        }
    }
}

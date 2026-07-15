package com.rkk.orderprocessing.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.rkk.orderprocessing.order.application.OrderProcessor;
import com.rkk.orderprocessing.order.domain.OrderStatus;
import com.rkk.orderprocessing.testsupport.PostgresTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL race proofs for the conditional mutation model.
 *
 * <p>Each contender opens a distinct Read Committed transaction and obtains its connection before
 * meeting at a barrier. Assertions are based on affected-row counts and committed final state;
 * there are no timing sleeps or assumptions about which contender wins.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class OrderConcurrencyIT {

    private static final Instant CREATED_AT = Instant.parse("2026-07-13T08:00:00Z");
    private static final Instant MUTATED_AT = Instant.parse("2026-07-13T08:05:00Z");

    @Autowired
    private OrderRepository repository;

    @Autowired
    private OrderProcessor pendingOrderProcessor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** Removes rows through the database so every setup is committed before worker transactions. */
    @BeforeEach
    @AfterEach
    void clearOrders() {
        jdbcTemplate.update("delete from orders");
    }

    @Test
    void cancelAndFiveMinuteJobHaveExactlyOneWinner() throws Exception {
        UUID orderId = savePending("RACE-CANCEL-JOB");
        CyclicBarrier startTogether = new CyclicBarrier(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<TransactionOutcome> cancellation = executor.submit(() -> inSeparateTransaction(
                    startTogether,
                    () -> repository.updateStatusIfExpected(
                            orderId,
                            OrderStatus.PENDING,
                            OrderStatus.CANCELLED,
                            MUTATED_AT)));
            Future<TransactionOutcome> job = executor.submit(() -> inSeparateTransaction(
                    startTogether,
                    pendingOrderProcessor::processPending));

            TransactionOutcome cancellationOutcome = cancellation.get(20, TimeUnit.SECONDS);
            TransactionOutcome jobOutcome = job.get(20, TimeUnit.SECONDS);

            assertDistinctConnections(cancellationOutcome, jobOutcome);
            assertThat(cancellationOutcome.affectedRows() + jobOutcome.affectedRows()).isOne();

            String finalStatus = statusOf(orderId);
            if (cancellationOutcome.affectedRows() == 1) {
                assertThat(jobOutcome.affectedRows()).isZero();
                assertThat(finalStatus).isEqualTo("CANCELLED");
            } else {
                assertThat(jobOutcome.affectedRows()).isOne();
                assertThat(finalStatus).isEqualTo("PROCESSING");
            }
        }
    }

    @Test
    void competingManualTransitionsHaveExactlyOneWinner() throws Exception {
        UUID orderId = savePending("RACE-MANUAL");
        CyclicBarrier startTogether = new CyclicBarrier(2);
        IntSupplier advancePendingToProcessing = () -> repository.updateStatusIfExpected(
                orderId,
                OrderStatus.PENDING,
                OrderStatus.PROCESSING,
                MUTATED_AT);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<TransactionOutcome> first = executor.submit(() ->
                    inSeparateTransaction(startTogether, advancePendingToProcessing));
            Future<TransactionOutcome> second = executor.submit(() ->
                    inSeparateTransaction(startTogether, advancePendingToProcessing));

            TransactionOutcome firstOutcome = first.get(20, TimeUnit.SECONDS);
            TransactionOutcome secondOutcome = second.get(20, TimeUnit.SECONDS);

            assertDistinctConnections(firstOutcome, secondOutcome);
            assertThat(List.of(firstOutcome.affectedRows(), secondOutcome.affectedRows()))
                    .containsExactlyInAnyOrder(0, 1);
            assertThat(statusOf(orderId)).isEqualTo("PROCESSING");
        }
    }

    @Test
    void overlappingJobsPromoteEachPendingRowAtMostOnce() throws Exception {
        List<UUID> orderIds = List.of(
                savePending("RACE-JOB-1"),
                savePending("RACE-JOB-2"),
                savePending("RACE-JOB-3"));
        CyclicBarrier startTogether = new CyclicBarrier(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<TransactionOutcome> first = executor.submit(() -> inSeparateTransaction(
                    startTogether,
                    pendingOrderProcessor::processPending));
            Future<TransactionOutcome> second = executor.submit(() -> inSeparateTransaction(
                    startTogether,
                    pendingOrderProcessor::processPending));

            TransactionOutcome firstOutcome = first.get(20, TimeUnit.SECONDS);
            TransactionOutcome secondOutcome = second.get(20, TimeUnit.SECONDS);

            assertDistinctConnections(firstOutcome, secondOutcome);
            assertThat(List.of(firstOutcome.affectedRows(), secondOutcome.affectedRows()))
                    .containsExactlyInAnyOrder(0, orderIds.size());
            assertThat(firstOutcome.affectedRows() + secondOutcome.affectedRows())
                    .isEqualTo(orderIds.size());
            assertThat(orderIds).allSatisfy(orderId ->
                    assertThat(statusOf(orderId)).isEqualTo("PROCESSING"));
        }
    }

    private TransactionOutcome inSeparateTransaction(
            CyclicBarrier startTogether,
            IntSupplier mutation) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        return Objects.requireNonNull(transaction.execute(status -> {
            Integer backendPid = jdbcTemplate.queryForObject(
                    "select pg_backend_pid()", Integer.class);
            await(startTogether);
            return new TransactionOutcome(
                    Objects.requireNonNull(backendPid),
                    mutation.getAsInt());
        }));
    }

    private UUID savePending(String productId) {
        UUID orderId = UUID.randomUUID();
        repository.saveAndFlush(OrderEntity.createPending(
                orderId,
                CREATED_AT,
                List.of(OrderItemEntity.create(0, productId, 1))));
        return orderId;
    }

    private String statusOf(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "select status from orders where id = ?",
                String.class,
                orderId);
    }

    private static void assertDistinctConnections(
            TransactionOutcome first,
            TransactionOutcome second) {
        assertThat(first.backendPid()).isNotEqualTo(second.backendPid());
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent contenders did not reach the barrier", exception);
        }
    }

    private record TransactionOutcome(int backendPid, int affectedRows) {}
}

package com.rkk.orderprocessing.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rkk.orderprocessing.testsupport.PostgresTestConfiguration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
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

/** PostgreSQL proof of the statement-snapshot boundary for the pending-order bulk update. */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class PendingOrderProcessorSnapshotIT {

    private static final Instant CREATED_AT = Instant.parse("2026-07-13T08:00:00Z");
    private static final OffsetDateTime DATABASE_CREATED_AT = CREATED_AT.atOffset(ZoneOffset.UTC);

    @Autowired
    private PendingOrderProcessor pendingOrderProcessor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void clearOrders() {
        jdbcTemplate.update("delete from orders");
    }

    @Test
    void rowCommittedAfterBulkUpdateBeginsWaitsForNextInvocation() throws Exception {
        UUID initiallyVisibleOrder = UUID.randomUUID();
        UUID lateOrder = UUID.randomUUID();
        insertPending(initiallyVisibleOrder, "SNAPSHOT-VISIBLE");

        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch workerConnected = new CountDownLatch(1);
        CountDownLatch releaseRowLock = new CountDownLatch(1);
        AtomicInteger holderPid = new AtomicInteger();
        AtomicInteger workerPid = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> lockHolder = executor.submit(() -> inSeparateReadCommittedTransaction(() -> {
                int pid = currentBackendPid();
                UUID lockedOrder = jdbcTemplate.queryForObject(
                        "select id from orders where id = ? for update",
                        UUID.class,
                        initiallyVisibleOrder);
                assertThat(lockedOrder).isEqualTo(initiallyVisibleOrder);
                holderPid.set(pid);
                rowLocked.countDown();
                await(releaseRowLock, "The processor did not reach its statement snapshot");
                return pid;
            }));

            await(rowLocked, "The setup transaction did not acquire its row lock");

            Future<ProcessingOutcome> firstRun = executor.submit(() ->
                    inSeparateReadCommittedTransaction(() -> {
                        int pid = currentBackendPid();
                        workerPid.set(pid);
                        workerConnected.countDown();
                        return new ProcessingOutcome(pid, pendingOrderProcessor.processPending());
                    }));

            await(workerConnected, "The processor transaction did not obtain a connection");
            try {
                awaitBlockedBy(workerPid.get(), holderPid.get());

                int lateInsertPid = inSeparateReadCommittedTransaction(() -> {
                    int pid = currentBackendPid();
                    insertPending(lateOrder, "SNAPSHOT-LATE");
                    return pid;
                });

                assertThat(lateInsertPid)
                        .isNotEqualTo(holderPid.get())
                        .isNotEqualTo(workerPid.get());
                assertThat(statusOf(lateOrder)).isEqualTo("PENDING");
            } finally {
                releaseRowLock.countDown();
            }

            assertThat(lockHolder.get(20, TimeUnit.SECONDS)).isEqualTo(holderPid.get());
            ProcessingOutcome firstOutcome = firstRun.get(20, TimeUnit.SECONDS);

            assertThat(firstOutcome.backendPid()).isEqualTo(workerPid.get());
            assertThat(firstOutcome.affectedRows()).isOne();
            assertThat(statusOf(initiallyVisibleOrder)).isEqualTo("PROCESSING");
            assertThat(statusOf(lateOrder)).isEqualTo("PENDING");

            assertThat(pendingOrderProcessor.processPending()).isOne();
            assertThat(statusOf(lateOrder)).isEqualTo("PROCESSING");
        }
    }

    /**
     * Waits for PostgreSQL itself to report that the processor is blocked by the lock holder.
     * This proves the bulk UPDATE statement has started and acquired its Read Committed snapshot.
     */
    private void awaitBlockedBy(int blockedPid, int blockingPid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            Boolean isBlocked = jdbcTemplate.queryForObject(
                    "select ? = any (pg_blocking_pids(?))",
                    Boolean.class,
                    blockingPid,
                    blockedPid);
            if (Boolean.TRUE.equals(isBlocked)) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("PostgreSQL did not report the expected processor lock wait");
    }

    private <T> T inSeparateReadCommittedTransaction(Supplier<T> work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return Objects.requireNonNull(transaction.execute(status -> {
            assertThat(jdbcTemplate.queryForObject(
                    "show transaction_isolation", String.class)).isEqualTo("read committed");
            return work.get();
        }));
    }

    private int currentBackendPid() {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "select pg_backend_pid()", Integer.class));
    }

    private void insertPending(UUID orderId, String productId) {
        jdbcTemplate.update(
                "insert into orders (id, status, created_at, updated_at) values (?, 'PENDING', ?, ?)",
                orderId,
                DATABASE_CREATED_AT,
                DATABASE_CREATED_AT);
        jdbcTemplate.update(
                "insert into order_items (order_id, position, product_id, quantity) values (?, 0, ?, 1)",
                orderId,
                productId);
    }

    private String statusOf(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "select status from orders where id = ?",
                String.class,
                orderId);
    }

    private static void await(CountDownLatch latch, String timeoutMessage) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException(timeoutMessage);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating PostgreSQL transactions", exception);
        }
    }

    private record ProcessingOutcome(int backendPid, int affectedRows) {}
}

package com.rkk.orderprocessing.order.job;

import com.rkk.orderprocessing.order.application.OrderProcessor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts pending-order processing every five minutes and records metrics for each run.
 * The application processor owns the transaction and decides which orders are updated; this class
 * only handles timing, logging, and metrics.
 *
 * <p>A feature flag (orders.scheduler.enabled) controls whether this component loads. This allows:
 * <ul>
 *   <li>Disabling the scheduler during tests to prevent unexpected database mutations.</li>
 *   <li>Running multiple API nodes while designating only one node as the active background worker.</li>
 *   <li>Using an emergency kill-switch in production without deploying new code.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "orders.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public final class OrderScheduler {

    public static final String CRON = "0 */5 * * * *";
    public static final String ZONE = "UTC";

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderScheduler.class);

    private final OrderProcessor processor;
    private final Timer duration;
    private final DistributionSummary affectedRows;
    private final Counter failures;

    /**
     * Initializes the scheduler and registers its metrics.
     *
     * @param processor the application service that performs the pending-order update.
     * @param meterRegistry the Micrometer registry for recording telemetry. By injecting this,
     *                      Spring Boot automatically exposes these metrics to external monitoring
     *                      systems (like Prometheus or Datadog) without vendor-specific code. This
     *                      allows DevOps to build dashboards and alerts for job duration, throughput,
     *                      and silent failures.
     */
    public OrderScheduler(OrderProcessor processor, MeterRegistry meterRegistry) {
        this.processor = processor;
        // A Timer tracks both the count of executions and the exact duration of each execution.
        // If this duration spikes, it's an early warning sign of database performance degradation.
        this.duration = Timer.builder("orders.pending.processing.duration")
                .description("Time spent promoting pending orders")
                .register(meterRegistry);

        // A DistributionSummary tracks the throughput of the system.
        // It records how many orders were successfully processed in each 5-minute window.
        this.affectedRows = DistributionSummary.builder("orders.pending.processing.rows")
                .description("Orders promoted by each scheduled run")
                .baseUnit("orders")
                .register(meterRegistry);

        // A Counter strictly increments. Since we deliberately catch exceptions so the scheduler
        // doesn't crash, this metric is critical for alerting DevOps to silent, background failures.
        this.failures = Counter.builder("orders.pending.processing.failures")
                .description("Scheduled pending-order runs that failed")
                .register(meterRegistry);
    }

    /**
     * Wakes up every 5 minutes to promote all {@code PENDING} orders to {@code PROCESSING}.
     *
     * <p><b>Idempotency & Safety:</b></p>
     * <ul>
     *   <li>If two nodes run this job at the exact same millisecond, the atomic SQL {@code WHERE} clause
     *       guarantees the orders are only processed once.</li>
     *   <li>Any database exceptions are swallowed to ensure the scheduler thread stays alive for the next tick.</li>
     * </ul>
     */
    @Scheduled(cron = CRON, zone = ZONE)
    public void processPendingOrders() {
        long startedAt = System.nanoTime();

        try {
            // 1. Execute the atomic database transaction
            int affectedCount = processor.processPending();

            // 2. Record success metrics and log the outcome
            long elapsedNanos = System.nanoTime() - startedAt;
            affectedRows.record(affectedCount);

            LOGGER.info(
                    "Pending order processing completed outcome=success affectedCount={} durationMs={}",
                    affectedCount,
                    Duration.ofNanos(elapsedNanos).toMillis());

        } catch (RuntimeException exception) {
            // 3. Record failure metrics but DO NOT rethrow.
            // Suppressing the exception here ensures the next cron tick still runs, and prevents
            // Spring's default TaskScheduler from loudly logging the raw stack trace.
            long elapsedNanos = System.nanoTime() - startedAt;
            failures.increment();

            LOGGER.error(
                    "Pending order processing completed outcome=failure durationMs={} exceptionType={}",
                    Duration.ofNanos(elapsedNanos).toMillis(),
                    exception.getClass().getName());

        } finally {
            // 4. Always record the exact total duration, regardless of success or failure
            duration.record(Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }
}

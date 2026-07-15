package com.rkk.orderprocessing.order.job;

import com.rkk.orderprocessing.order.application.PendingOrderProcessor;
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
 */
@Component
@ConditionalOnProperty(prefix = "orders.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public final class PendingOrderScheduler {

    public static final String CRON = "0 */5 * * * *";
    public static final String ZONE = "UTC";

    private static final Logger LOGGER = LoggerFactory.getLogger(PendingOrderScheduler.class);

    private final PendingOrderProcessor processor;
    private final Timer duration;
    private final DistributionSummary affectedRows;
    private final Counter failures;

    /**
     * Initializes the scheduler and registers its metrics.
     *
     * @param processor the application service that performs the pending-order update.
     * @param meterRegistry the Micrometer registry for recording telemetry.
     */
    public PendingOrderScheduler(PendingOrderProcessor processor, MeterRegistry meterRegistry) {
        this.processor = processor;
        this.duration = Timer.builder("orders.pending.processing.duration")
                .description("Time spent promoting pending orders")
                .register(meterRegistry);
        this.affectedRows = DistributionSummary.builder("orders.pending.processing.rows")
                .description("Orders promoted by each scheduled run")
                .baseUnit("orders")
                .register(meterRegistry);
        this.failures = Counter.builder("orders.pending.processing.failures")
                .description("Scheduled pending-order runs that failed")
                .register(meterRegistry);
    }

    /**
     * Asks the processor to change all orders still {@code PENDING} to {@code PROCESSING}.
     * Running the job again is safe because orders already moved out of {@code PENDING} no longer
     * match the database update.
     */
    @Scheduled(cron = CRON, zone = ZONE)
    public void processPendingOrders() {
        long startedAt = System.nanoTime();
        try {
            int affectedCount = processor.processPending();
            long elapsedNanos = System.nanoTime() - startedAt;
            duration.record(Duration.ofNanos(elapsedNanos));
            affectedRows.record(affectedCount);
            LOGGER.info(
                    "Pending order processing completed outcome=success affectedCount={} durationMs={}",
                    affectedCount,
                    Duration.ofNanos(elapsedNanos).toMillis());
        } catch (RuntimeException exception) {
            long elapsedNanos = System.nanoTime() - startedAt;
            duration.record(Duration.ofNanos(elapsedNanos));
            failures.increment();
            LOGGER.error(
                    "Pending order processing completed outcome=failure durationMs={} exceptionType={}",
                    Duration.ofNanos(elapsedNanos).toMillis(),
                    exception.getClass().getName());
            // This adapter is the terminal scheduled boundary. Suppressing here preserves the
            // next cron run and prevents Spring's default handler from logging the raw throwable.
        }
    }
}

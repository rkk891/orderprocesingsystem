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
 * UTC time adapter for pending-order processing.
 *
 * <p>Business and transaction logic deliberately stays in {@link PendingOrderProcessor}; this
 * component owns only scheduling and safe operational telemetry.
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

    /** Runs one idempotent, set-based pending-order promotion. */
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

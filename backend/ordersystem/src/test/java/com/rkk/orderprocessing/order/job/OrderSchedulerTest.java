package com.rkk.orderprocessing.order.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rkk.orderprocessing.order.application.OrderProcessor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class OrderSchedulerTest {

    @Test
    void delegatesOneRunAndRecordsAffectedRows() {
        OrderProcessor processor = org.mockito.Mockito.mock(OrderProcessor.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(processor.processPending()).thenReturn(3);
        OrderScheduler scheduler = new OrderScheduler(processor, meterRegistry);

        scheduler.processPendingOrders();

        verify(processor).processPending();
        assertThat(meterRegistry.get("orders.pending.processing.rows").summary().count()).isEqualTo(1);
        assertThat(meterRegistry.get("orders.pending.processing.rows").summary().totalAmount()).isEqualTo(3);
        assertThat(meterRegistry.get("orders.pending.processing.duration").timer().count()).isEqualTo(1);
    }

    @Test
    void declaresTheFixedUtcFiveMinuteSchedule() throws NoSuchMethodException {
        Method scheduledMethod = OrderScheduler.class.getMethod("processPendingOrders");
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 */5 * * * *");
        assertThat(scheduled.zone()).isEqualTo("UTC");
    }

    @Test
    void recordsAndSuppressesFailedRunsWithoutRecordingAffectedRows() {
        OrderProcessor processor = org.mockito.Mockito.mock(OrderProcessor.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(processor.processPending()).thenThrow(new IllegalStateException("database unavailable"));
        OrderScheduler scheduler = new OrderScheduler(processor, meterRegistry);

        scheduler.processPendingOrders();

        assertThat(meterRegistry.get("orders.pending.processing.failures").counter().count())
                .isEqualTo(1);
        assertThat(meterRegistry.get("orders.pending.processing.duration").timer().count())
                .isEqualTo(1);
        assertThat(meterRegistry.find("orders.pending.processing.rows").summary())
                .isNotNull()
                .satisfies(summary -> assertThat(summary.count()).isZero());
    }
}

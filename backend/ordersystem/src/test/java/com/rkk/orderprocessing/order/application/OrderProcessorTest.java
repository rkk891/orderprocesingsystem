package com.rkk.orderprocessing.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rkk.orderprocessing.order.persistence.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Direct test of the transactional handler without waiting for wall-clock scheduling. */
@ExtendWith(MockitoExtension.class)
class OrderProcessorTest {

    @Mock
    private OrderRepository repository;

    @Test
    void capturesOneClockInstantAndReturnsTheBulkAffectedCount() {
        Instant now = Instant.parse("2026-07-11T12:30:00Z");
        when(repository.processPending(now)).thenReturn(7);
        OrderProcessor processor = new OrderProcessor(
                repository,
                Clock.fixed(now, ZoneOffset.UTC));

        assertThat(processor.processPending()).isEqualTo(7);
        verify(repository).processPending(now);
    }
}

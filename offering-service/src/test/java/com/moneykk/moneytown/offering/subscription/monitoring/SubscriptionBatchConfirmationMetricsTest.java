package com.moneykk.moneytown.offering.subscription.monitoring;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SubscriptionBatchConfirmationMetricsTest {

    @Test
    @DisplayName("일괄 확정 시간과 확정 청약 수를 기록한다")
    void recordsDurationAndConfirmedCount() {
        SimpleMeterRegistry meterRegistry =
                new SimpleMeterRegistry();

        ApplicationEventPublisher eventPublisher =
                mock(ApplicationEventPublisher.class);

        SubscriptionBatchConfirmationMetrics metrics =
                new SubscriptionBatchConfirmationMetrics(
                        eventPublisher,
                        meterRegistry
                );

        metrics.publish(
                Duration.ofMillis(125),
                1_000
        );

        ArgumentCaptor<SubscriptionBatchConfirmationMetrics
                .BatchConfirmationMetricEvent> captor =
                ArgumentCaptor.forClass(
                        SubscriptionBatchConfirmationMetrics
                                .BatchConfirmationMetricEvent.class
                );

        verify(eventPublisher).publishEvent(captor.capture());

        metrics.record(captor.getValue());

        assertThat(
                meterRegistry
                        .get("subscription.batch.confirmation.duration")
                        .timer()
                        .totalTime(TimeUnit.MILLISECONDS)
        ).isEqualTo(125.0);

        assertThat(
                meterRegistry
                        .get("subscription.batch.confirmation.size")
                        .summary()
                        .totalAmount()
        ).isEqualTo(1_000.0);

        meterRegistry.close();
    }
}

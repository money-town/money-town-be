package com.moneykk.moneytown.offering.subscription.monitoring;

import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionLifecycleMetricsTest {

    private ApplicationEventPublisher eventPublisher;
    private SimpleMeterRegistry meterRegistry;
    private SubscriptionLifecycleMetrics metrics;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        meterRegistry = new SimpleMeterRegistry();

        metrics = new SubscriptionLifecycleMetrics(
                eventPublisher,
                meterRegistry
        );
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    @DisplayName("청약 생성 시각부터 완료 시각까지의 메트릭 이벤트를 발행한다")
    void publishesLifecycleMetricEvent() {
        Subscription subscription = mock(Subscription.class);

        Instant createdAt =
                Instant.parse("2026-09-14T00:00:00Z");

        Instant completedAt =
                createdAt.plusSeconds(30);

        when(subscription.getCreatedAt())
                .thenReturn(createdAt);

        metrics.publishOutcome(
                subscription,
                SubscriptionLifecycleMetrics.Result.CONFIRMED,
                completedAt
        );

        ArgumentCaptor<
                SubscriptionLifecycleMetrics.LifecycleMetricEvent
                > captor =
                ArgumentCaptor.forClass(
                        SubscriptionLifecycleMetrics
                                .LifecycleMetricEvent.class
                );

        verify(eventPublisher)
                .publishEvent(captor.capture());

        assertThat(captor.getValue().result())
                .isEqualTo(
                        SubscriptionLifecycleMetrics.Result.CONFIRMED
                );

        assertThat(captor.getValue().duration())
                .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("커밋된 생명주기 결과의 횟수와 처리 시간을 기록한다")
    void recordsCommittedOutcome() {
        metrics.recordOutcome(
                new SubscriptionLifecycleMetrics.LifecycleMetricEvent(
                        SubscriptionLifecycleMetrics.Result.REJECTED,
                        Duration.ofSeconds(5)
                )
        );

        double outcomeCount = meterRegistry
                .get("subscription.lifecycle.outcomes")
                .tag("result", "rejected")
                .counter()
                .count();

        Timer timer = meterRegistry
                .get("subscription.lifecycle.duration")
                .tag("result", "rejected")
                .timer();

        assertThat(outcomeCount).isEqualTo(1.0);
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(TimeUnit.SECONDS))
                .isEqualTo(5.0);
    }
}
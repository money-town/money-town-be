package com.moneykk.moneytown.offering.offering.command.scheduler;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfferingSchedulerMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private OfferingSchedulerMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new OfferingSchedulerMetrics(meterRegistry);
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    @DisplayName("공모 상태 전환 스케줄러별 실패 횟수를 기록한다")
    void recordsSchedulerFailures() {
        metrics.recordOpenScheduledFailure();
        metrics.recordCloseSoldOutFailure();
        metrics.recordUnderSubscribedCancellationFailure();

        assertThat(failureCount("open_scheduled"))
                .isEqualTo(1.0);

        assertThat(failureCount("close_sold_out"))
                .isEqualTo(1.0);

        assertThat(failureCount("under_subscribed_cancellation"))
                .isEqualTo(1.0);
    }

    private double failureCount(String scheduler) {
        return meterRegistry
                .get("offering.scheduler.failures")
                .tag("scheduler", scheduler)
                .counter()
                .count();
    }
}
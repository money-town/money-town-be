package com.moneykk.moneytown.offering.offering.command.scheduler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OfferingSchedulerMetrics {

    private static final String METRIC_NAME =
            "offering.scheduler.failures";

    private final Counter openScheduledFailure;
    private final Counter closeSoldOutFailure;
    private final Counter underSubscribedCancellationFailure;

    public OfferingSchedulerMetrics(MeterRegistry meterRegistry) {
        this.openScheduledFailure = registerCounter(
                meterRegistry,
                "open_scheduled"
        );

        this.closeSoldOutFailure = registerCounter(
                meterRegistry,
                "close_sold_out"
        );

        this.underSubscribedCancellationFailure = registerCounter(
                meterRegistry,
                "under_subscribed_cancellation"
        );
    }

    public void recordOpenScheduledFailure() {
        openScheduledFailure.increment();
    }

    public void recordCloseSoldOutFailure() {
        closeSoldOutFailure.increment();
    }

    public void recordUnderSubscribedCancellationFailure() {
        underSubscribedCancellationFailure.increment();
    }

    private Counter registerCounter(
            MeterRegistry meterRegistry,
            String scheduler
    ) {
        return Counter.builder(METRIC_NAME)
                .description("공모 상태 전환 스케줄러 실패 횟수")
                .tag("scheduler", scheduler)
                .register(meterRegistry);
    }
}
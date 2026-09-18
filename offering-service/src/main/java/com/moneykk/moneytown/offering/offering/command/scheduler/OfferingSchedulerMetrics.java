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
    private final Counter underSubscribedItemFailure;
    private final Counter offeringCancellationBatchFailure;
    private final Counter subscriptionTimeoutBatchFailure;
    private final Counter subscriptionTimeoutItemFailure;
    private final Counter idempotencyRecoveryFailure;
    private final Counter outboxPublishBatchFailure;
    private final Counter outboxRecoveryFailure;

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

        this.underSubscribedItemFailure = registerCounter(
                meterRegistry,
                "under_subscribed_item"
        );

        this.offeringCancellationBatchFailure = registerCounter(
                meterRegistry,
                "offering_cancellation_batch"
        );

        this.subscriptionTimeoutBatchFailure = registerCounter(
                meterRegistry,
                "subscription_timeout_batch"
        );

        this.subscriptionTimeoutItemFailure = registerCounter(
                meterRegistry,
                "subscription_timeout_item"
        );

        this.idempotencyRecoveryFailure = registerCounter(
                meterRegistry,
                "idempotency_recovery"
        );

        this.outboxPublishBatchFailure = registerCounter(
                meterRegistry,
                "outbox_publish_batch"
        );

        this.outboxRecoveryFailure = registerCounter(
                meterRegistry,
                "outbox_recovery"
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

    public void recordUnderSubscribedItemFailure() {
        underSubscribedItemFailure.increment();
    }

    public void recordOfferingCancellationBatchFailure() {
        offeringCancellationBatchFailure.increment();
    }

    public void recordSubscriptionTimeoutBatchFailure() {
        subscriptionTimeoutBatchFailure.increment();
    }

    public void recordSubscriptionTimeoutItemFailure() {
        subscriptionTimeoutItemFailure.increment();
    }

    public void recordIdempotencyRecoveryFailure() {
        idempotencyRecoveryFailure.increment();
    }

    public void recordOutboxPublishBatchFailure() {
        outboxPublishBatchFailure.increment();
    }

    public void recordOutboxRecoveryFailure() {
        outboxRecoveryFailure.increment();
    }

    private Counter registerCounter(
            MeterRegistry meterRegistry,
            String scheduler
    ) {
        return Counter.builder(METRIC_NAME)
                .description("Offering Service 스케줄러 실패 횟수")
                .tag("scheduler", scheduler)
                .register(meterRegistry);
    }
}

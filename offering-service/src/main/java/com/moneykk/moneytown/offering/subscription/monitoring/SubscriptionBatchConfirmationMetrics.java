package com.moneykk.moneytown.offering.subscription.monitoring;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.Objects;

@Component
public class SubscriptionBatchConfirmationMetrics {

    private final ApplicationEventPublisher eventPublisher;
    private final Timer durationTimer;
    private final DistributionSummary confirmedCountSummary;

    public SubscriptionBatchConfirmationMetrics(
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry
    ) {
        this.eventPublisher = eventPublisher;

        this.durationTimer = Timer.builder(
                        "subscription.batch.confirmation.duration"
                )
                .description(
                        "청약 일괄 확정 준비 조회부터 상태 및 Outbox 반영까지의 시간"
                )
                .maximumExpectedValue(Duration.ofMinutes(1))
                .serviceLevelObjectives(
                        Duration.ofMillis(100),
                        Duration.ofMillis(250),
                        Duration.ofMillis(500),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(1)
                )
                .publishPercentileHistogram()
                .register(meterRegistry);

        this.confirmedCountSummary = DistributionSummary.builder(
                        "subscription.batch.confirmation.size"
                )
                .description(
                        "한 번의 일괄 확정에서 새로 확정된 청약 수"
                )
                .serviceLevelObjectives(
                        10,
                        100,
                        500,
                        1_000,
                        5_000,
                        10_000
                )
                .register(meterRegistry);
    }

    public void publish(
            Duration duration,
            int confirmedCount
    ) {
        Objects.requireNonNull(duration, "duration은 필수입니다.");

        if (duration.isNegative()) {
            throw new IllegalArgumentException(
                    "일괄 확정 시간은 음수일 수 없습니다."
            );
        }

        if (confirmedCount <= 0) {
            throw new IllegalArgumentException(
                    "확정 청약 수는 1 이상이어야 합니다."
            );
        }

        eventPublisher.publishEvent(
                new BatchConfirmationMetricEvent(
                        duration,
                        confirmedCount
                )
        );
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void record(
            BatchConfirmationMetricEvent event
    ) {
        durationTimer.record(event.duration());
        confirmedCountSummary.record(event.confirmedCount());
    }

    public record BatchConfirmationMetricEvent(
            Duration duration,
            int confirmedCount
    ) {
    }
}

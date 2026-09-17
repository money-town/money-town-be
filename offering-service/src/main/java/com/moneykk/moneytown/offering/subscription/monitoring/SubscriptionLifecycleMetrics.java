package com.moneykk.moneytown.offering.subscription.monitoring;

import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

@Component
public class SubscriptionLifecycleMetrics {

    private final ApplicationEventPublisher eventPublisher;

    private final Map<Result, Counter> outcomeCounters =
            new EnumMap<>(Result.class);

    private final Map<Result, Timer> durationTimers =
            new EnumMap<>(Result.class);

    public SubscriptionLifecycleMetrics(
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry
    ) {
        this.eventPublisher = eventPublisher;

        for (Result result : Result.values()) {
            String tagValue =
                    result.name().toLowerCase();

            outcomeCounters.put(
                    result,
                    Counter.builder(
                                    "subscription.lifecycle.outcomes"
                            )
                            .description(
                                    "청약 생명주기 결과 전환 횟수"
                            )
                            .tag("result", tagValue)
                            .register(meterRegistry)
            );

            durationTimers.put(
                    result,
                    Timer.builder(
                                    "subscription.lifecycle.duration"
                            )
                            .description(
                                    "청약 생성부터 결과 상태 전환까지 소요 시간"
                            )
                            .tag("result", tagValue)
                            .maximumExpectedValue(
                                    Duration.ofDays(7)
                            )
                            .serviceLevelObjectives(
                                    Duration.ofSeconds(1),
                                    Duration.ofSeconds(5),
                                    Duration.ofSeconds(30),
                                    Duration.ofMinutes(1),
                                    Duration.ofMinutes(5),
                                    Duration.ofMinutes(15),
                                    Duration.ofHours(1),
                                    Duration.ofHours(6),
                                    Duration.ofDays(1),
                                    Duration.ofDays(3),
                                    Duration.ofDays(7)
                            )
                            .publishPercentileHistogram()
                            .register(meterRegistry)
            );
        }
    }

    /**
     * 현재 트랜잭션이 커밋된 후 기록할 메트릭 이벤트를 발행한다.
     */
    public void publishOutcome(
            Subscription subscription,
            Result result,
            Instant completedAt
    ) {
        Objects.requireNonNull(subscription);
        Objects.requireNonNull(result);
        Objects.requireNonNull(completedAt);

        Duration duration = null;
        Instant createdAt = subscription.getCreatedAt();

        if (createdAt != null
                && !completedAt.isBefore(createdAt)) {
            duration = Duration.between(
                    createdAt,
                    completedAt
            );
        }

        eventPublisher.publishEvent(
                new LifecycleMetricEvent(
                        result,
                        duration
                )
        );
    }

    /**
     * DB 트랜잭션이 실제로 커밋된 경우에만 메트릭을 반영한다.
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void recordOutcome(
            LifecycleMetricEvent event
    ) {
        outcomeCounters.get(event.result())
                .increment();

        if (event.duration() != null) {
            durationTimers.get(event.result())
                    .record(event.duration());
        }
    }

    public enum Result {
        CONFIRMED,
        REJECTED,
        CANCELLED,
        MANUAL_REVIEW
    }

    public record LifecycleMetricEvent(
            Result result,
            Duration duration
    ) {
    }
}

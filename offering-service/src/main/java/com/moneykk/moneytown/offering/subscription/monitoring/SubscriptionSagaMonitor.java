package com.moneykk.moneytown.offering.subscription.monitoring;

import com.moneykk.moneytown.offering.subscription.domain.entity.HoldingAllocationStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

@Slf4j
@Component
public class SubscriptionSagaMonitor {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionCompensationRepository
            subscriptionCompensationRepository;

    private final long holdingAllocationStuckSeconds;
    private final long compensationStuckSeconds;

    private final AtomicLong walletHoldStuckCount =
            new AtomicLong();

    private final AtomicLong holdingAllocationStuckCount =
            new AtomicLong();

    private final AtomicLong compensationStuckCount =
            new AtomicLong();

    private final AtomicLong manualReviewCount =
            new AtomicLong();

    public SubscriptionSagaMonitor(
            SubscriptionRepository subscriptionRepository,
            SubscriptionCompensationRepository
                    subscriptionCompensationRepository,
            MeterRegistry meterRegistry,

            @Value(
                    "${subscription.monitoring."
                            + "holding-allocation-stuck-seconds:300}"
            )
            long holdingAllocationStuckSeconds,

            @Value(
                    "${subscription.monitoring."
                            + "compensation-stuck-seconds:300}"
            )
            long compensationStuckSeconds
    ) {
        if (holdingAllocationStuckSeconds <= 0
                || compensationStuckSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Saga 고착 판정 시간은 1초 이상이어야 합니다."
            );
        }

        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionCompensationRepository =
                subscriptionCompensationRepository;
        this.holdingAllocationStuckSeconds =
                holdingAllocationStuckSeconds;
        this.compensationStuckSeconds =
                compensationStuckSeconds;

        registerStuckGauge(
                meterRegistry,
                "wallet_hold",
                walletHoldStuckCount
        );

        registerStuckGauge(
                meterRegistry,
                "holding_allocation",
                holdingAllocationStuckCount
        );

        registerStuckGauge(
                meterRegistry,
                "compensation",
                compensationStuckCount
        );

        Gauge.builder(
                        "subscription.manual.review.count",
                        manualReviewCount,
                        AtomicLong::get
                )
                .description(
                        "운영자의 확인이 필요한 MANUAL_REVIEW 청약 수"
                )
                .register(meterRegistry);
    }

    private void registerStuckGauge(
            MeterRegistry meterRegistry,
            String stage,
            AtomicLong count
    ) {
        Gauge.builder(
                        "subscription.saga.stuck",
                        count,
                        AtomicLong::get
                )
                .description(
                        "일정 시간 이상 완료되지 않은 청약 Saga 수"
                )
                .tag("stage", stage)
                .register(meterRegistry);
    }

    @Scheduled(
            initialDelayString =
                    "${subscription.monitoring.refresh-delay-ms:10000}",
            fixedDelayString =
                    "${subscription.monitoring.refresh-delay-ms:10000}"
    )
    public void refresh() {
        Instant now = Instant.now();

        refreshMetric(
                "wallet_hold",
                walletHoldStuckCount,
                () -> subscriptionRepository
                        .countBySubscriptionStatusAndReservationExpiresAtLessThanEqualAndIsDeletedFalse(
                                SubscriptionStatus.PROCESSING,
                                now
                        )
        );

        Instant holdingStuckBefore =
                now.minusSeconds(
                        holdingAllocationStuckSeconds
                );

        refreshMetric(
                "holding_allocation",
                holdingAllocationStuckCount,
                () -> subscriptionRepository
                        .countBySubscriptionStatusAndHoldingAllocationStatusAndUpdatedAtLessThanEqualAndIsDeletedFalse(
                                SubscriptionStatus.CONFIRMED,
                                HoldingAllocationStatus.PENDING,
                                holdingStuckBefore
                        )
        );

        Instant compensationStuckBefore =
                now.minusSeconds(compensationStuckSeconds);

        refreshMetric(
                "compensation",
                compensationStuckCount,
                () -> subscriptionCompensationRepository
                        .countStuckCompensations(
                                compensationStuckBefore
                        )
        );

        refreshMetric(
                "manual_review",
                manualReviewCount,
                () -> subscriptionRepository
                        .countBySubscriptionStatusAndIsDeletedFalse(
                                SubscriptionStatus.MANUAL_REVIEW
                        )
        );
    }

    private void refreshMetric(
            String metric,
            AtomicLong target,
            LongSupplier valueSupplier
    ) {
        try {
            target.set(valueSupplier.getAsLong());
        } catch (Exception e) {
            log.warn(
                    "청약 운영 메트릭 갱신 실패. metric={}",
                    metric,
                    e
            );
        }
    }
}
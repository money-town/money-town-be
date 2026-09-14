package com.moneykk.moneytown.offering.subscription.monitoring;

import com.moneykk.moneytown.offering.subscription.domain.entity.HoldingAllocationStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionSagaMonitorTest {

    private SubscriptionRepository subscriptionRepository;
    private SubscriptionCompensationRepository
            subscriptionCompensationRepository;

    private SimpleMeterRegistry meterRegistry;
    private SubscriptionSagaMonitor monitor;

    @BeforeEach
    void setUp() {
        subscriptionRepository =
                mock(SubscriptionRepository.class);

        subscriptionCompensationRepository =
                mock(SubscriptionCompensationRepository.class);

        meterRegistry = new SimpleMeterRegistry();

        monitor = new SubscriptionSagaMonitor(
                subscriptionRepository,
                subscriptionCompensationRepository,
                meterRegistry,
                300,
                300
        );
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    @DisplayName("청약 Saga 고착 상태와 수동 확인 건수를 Gauge에 반영한다")
    void refreshesSagaGauges() {
        when(subscriptionRepository
                .countBySubscriptionStatusAndReservationExpiresAtLessThanEqualAndIsDeletedFalse(
                        eq(SubscriptionStatus.PROCESSING),
                        any(Instant.class)
                ))
                .thenReturn(2L);

        when(subscriptionRepository
                .countBySubscriptionStatusAndHoldingAllocationStatusAndUpdatedAtLessThanEqualAndIsDeletedFalse(
                        eq(SubscriptionStatus.CONFIRMED),
                        eq(HoldingAllocationStatus.PENDING),
                        any(Instant.class)
                ))
                .thenReturn(3L);

        when(subscriptionCompensationRepository
                .countStuckCompensations(any(Instant.class)))
                .thenReturn(4L);

        when(subscriptionRepository
                .countBySubscriptionStatusAndIsDeletedFalse(
                        SubscriptionStatus.MANUAL_REVIEW
                ))
                .thenReturn(5L);

        monitor.refresh();

        assertThat(stuckCount("wallet_hold"))
                .isEqualTo(2.0);

        assertThat(stuckCount("holding_allocation"))
                .isEqualTo(3.0);

        assertThat(stuckCount("compensation"))
                .isEqualTo(4.0);

        assertThat(meterRegistry
                .get("subscription.manual.review.count")
                .gauge()
                .value())
                .isEqualTo(5.0);
    }

    @Test
    @DisplayName("한 Saga 건수 조회가 실패해도 나머지 Gauge는 갱신한다")
    void continuesRefreshingWhenOneQueryFails() {
        when(subscriptionRepository
                .countBySubscriptionStatusAndReservationExpiresAtLessThanEqualAndIsDeletedFalse(
                        eq(SubscriptionStatus.PROCESSING),
                        any(Instant.class)
                ))
                .thenThrow(new RuntimeException("DB 조회 실패"));

        when(subscriptionRepository
                .countBySubscriptionStatusAndHoldingAllocationStatusAndUpdatedAtLessThanEqualAndIsDeletedFalse(
                        eq(SubscriptionStatus.CONFIRMED),
                        eq(HoldingAllocationStatus.PENDING),
                        any(Instant.class)
                ))
                .thenReturn(3L);

        when(subscriptionCompensationRepository
                .countStuckCompensations(any(Instant.class)))
                .thenReturn(4L);

        when(subscriptionRepository
                .countBySubscriptionStatusAndIsDeletedFalse(
                        SubscriptionStatus.MANUAL_REVIEW
                ))
                .thenReturn(5L);

        monitor.refresh();

        assertThat(stuckCount("wallet_hold"))
                .isZero();

        assertThat(stuckCount("holding_allocation"))
                .isEqualTo(3.0);

        assertThat(stuckCount("compensation"))
                .isEqualTo(4.0);

        assertThat(meterRegistry
                .get("subscription.manual.review.count")
                .gauge()
                .value())
                .isEqualTo(5.0);
    }

    private double stuckCount(String stage) {
        return meterRegistry
                .get("subscription.saga.stuck")
                .tag("stage", stage)
                .gauge()
                .value();
    }
}
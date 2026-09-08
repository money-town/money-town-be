package com.moneykk.moneytown.offering.subscription.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionCompensationTest {

    @Test
    @DisplayName("일반 보상은 Wallet과 Holding 모두 대기 상태로 생성한다")
    void createsGeneralCompensationWithPendingExternalSteps() {
        // given
        UUID subscriptionId = UUID.randomUUID();

        // when
        SubscriptionCompensation compensation =
                SubscriptionCompensation.create(subscriptionId);

        // then
        assertThat(compensation.getCompensationId()).isNotNull();
        assertThat(compensation.getSubscriptionId())
                .isEqualTo(subscriptionId);

        assertThat(compensation.getWalletStatus())
                .isEqualTo(CompensationStatus.PENDING);
        assertThat(compensation.getHoldingStatus())
                .isEqualTo(CompensationStatus.PENDING);

        assertThat(compensation.getWalletErrorCode()).isNull();
        assertThat(compensation.getHoldingErrorCode()).isNull();
        assertThat(compensation.isExternalCompensationCompleted())
                .isFalse();
    }

    @Test
    @DisplayName("예약 만료 보상은 Wallet 대기, Holding 완료 상태로 생성한다")
    void createsReservationExpirationCompensationWaitingOnlyForWallet() {
        // given
        UUID subscriptionId = UUID.randomUUID();

        // when
        SubscriptionCompensation compensation =
                SubscriptionCompensation
                        .createForReservationExpiration(subscriptionId);

        // then
        assertThat(compensation.getCompensationId()).isNotNull();
        assertThat(compensation.getSubscriptionId())
                .isEqualTo(subscriptionId);

        assertThat(compensation.getWalletStatus())
                .isEqualTo(CompensationStatus.PENDING);
        assertThat(compensation.getHoldingStatus())
                .isEqualTo(CompensationStatus.SUCCEEDED);

        assertThat(compensation.getWalletErrorCode()).isNull();
        assertThat(compensation.getHoldingErrorCode()).isNull();
        assertThat(compensation.isExternalCompensationCompleted())
                .isFalse();
    }

    @Test
    @DisplayName("예약 만료 보상은 Wallet 보상이 성공하면 외부 보상이 완료된다")
    void completesReservationExpirationCompensationAfterWalletSuccess() {
        // given
        SubscriptionCompensation compensation =
                SubscriptionCompensation
                        .createForReservationExpiration(
                                UUID.randomUUID()
                        );

        // when
        compensation.markWalletSucceeded();

        // then
        assertThat(compensation.getWalletStatus())
                .isEqualTo(CompensationStatus.SUCCEEDED);
        assertThat(compensation.getHoldingStatus())
                .isEqualTo(CompensationStatus.SUCCEEDED);
        assertThat(compensation.isExternalCompensationCompleted())
                .isTrue();
    }
}
package com.moneykk.moneytown.offering.subscription.domain.entity;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTest {

    @Test
    @DisplayName("PROCESSING 청약은 Wallet HOLD 성공 시 HOLD_SUCCEEDED로 전환된다")
    void marksHoldSucceeded() {
        Subscription subscription = createSubscription();

        subscription.markHoldSucceeded();

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.HOLD_SUCCEEDED);
        assertThat(subscription.getConfirmedAt()).isNull();
        assertThat(subscription.getHoldingAllocationStatus()).isNull();
    }

    @Test
    @DisplayName("HOLD_SUCCEEDED 청약은 최종 확정할 수 있다")
    void confirmsHoldSucceededSubscription() {
        Subscription subscription = createSubscription();
        Instant confirmedAt = Instant.now();

        subscription.markHoldSucceeded();
        subscription.confirm(confirmedAt);

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.CONFIRMED);
        assertThat(subscription.getConfirmedAt())
                .isEqualTo(confirmedAt);
        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.PENDING);
    }

    @Test
    @DisplayName("PROCESSING 청약은 HOLD 성공 없이 바로 확정할 수 없다")
    void cannotConfirmProcessingSubscription() {
        Subscription subscription = createSubscription();

        assertThatThrownBy(() ->
                subscription.confirm(Instant.now())
        ).isInstanceOf(BusinessException.class)
         .satisfies(exception ->
                assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(
                        SubscriptionErrorCode
                                .SUBSCRIPTION_CONFIRMATION_NOT_ALLOWED
                )
        );
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.PROCESSING);
    }

    @Test
    @DisplayName("HOLD_SUCCEEDED 청약은 공모 취소 보상을 시작할 수 있다")
    void startsCompensationFromHoldSucceeded() {
        Subscription subscription = createSubscription();

        subscription.markHoldSucceeded();
        subscription.startCompensation(
                CancellationType.OFFERING_UNDER_SUBSCRIBED
        );

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);
        assertThat(subscription.getCancellationType())
                .isEqualTo(CancellationType.OFFERING_UNDER_SUBSCRIBED);
    }

    @Test
    @DisplayName("청약 확정 시 Holding 배정 후처리를 PENDING으로 시작한다")
    void startsHoldingAllocationWhenConfirmed() {
        // given
        Subscription subscription = createSubscription();

        // when
        subscription.markHoldSucceeded();
        subscription.confirm(Instant.now());

        // then
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.CONFIRMED);

        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.PENDING);

        assertThat(subscription.getHoldingAllocationErrorCode())
                .isNull();
    }

    @Test
    @DisplayName("Holding 배정 실패를 기록해도 청약은 CONFIRMED로 유지한다")
    void recordsHoldingAllocationFailure() {
        // given
        Subscription subscription = createSubscription();
        subscription.markHoldSucceeded();
        subscription.confirm(Instant.now());

        // when
        subscription.markHoldingAllocationFailed(
                "HOLDING_ALLOCATION_FAILED"
        );

        // then
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.CONFIRMED);

        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.FAILED);

        assertThat(subscription.getHoldingAllocationErrorCode())
                .isEqualTo("HOLDING_ALLOCATION_FAILED");
    }

    @Test
    @DisplayName("Holding 배정 실패 후 성공하면 오류 코드를 지운다")
    void completesHoldingAllocationAfterFailure() {
        // given
        Subscription subscription = createSubscription();
        subscription.markHoldSucceeded();
        subscription.confirm(Instant.now());

        subscription.markHoldingAllocationFailed(
                "HOLDING_ALLOCATION_FAILED"
        );

        // when
        subscription.markHoldingAllocationSucceeded();

        // then
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.CONFIRMED);

        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.SUCCEEDED);

        assertThat(subscription.getHoldingAllocationErrorCode())
                .isNull();
    }

    @Test
    @DisplayName("Holding 배정 성공 후 늦은 실패가 도착해도 성공을 유지한다")
    void preservesHoldingAllocationSuccessAgainstLateFailure() {
        // given
        Subscription subscription = createSubscription();
        subscription.markHoldSucceeded();
        subscription.confirm(Instant.now());
        subscription.markHoldingAllocationSucceeded();

        // when
        subscription.markHoldingAllocationFailed(
                "LATE_ALLOCATION_FAILED"
        );

        // then
        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.SUCCEEDED);

        assertThat(subscription.getHoldingAllocationErrorCode())
                .isNull();
    }

    @Test
    @DisplayName("지분 배정이 시작되지 않은 청약에는 배정 결과를 기록할 수 없다")
    void cannotRecordHoldingAllocationBeforeConfirmation() {
        // given
        Subscription subscription = createSubscription();

        // when & then
        assertThatThrownBy(
                subscription::markHoldingAllocationSucceeded
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception).getErrorCode()
                        ).isEqualTo(
                                SubscriptionErrorCode
                                        .SUBSCRIPTION_CONFIRMATION_NOT_ALLOWED
                        )
                );
    }

    @Test
    @DisplayName("PROCESSING 청약은 모집 미달 보상을 시작하면 COMPENSATING으로 전환된다")
    void startsCompensationFromProcessing() {
        // given
        Subscription subscription = createSubscription();

        // when
        subscription.startCompensation(
                CancellationType.OFFERING_UNDER_SUBSCRIBED
        );

        // then
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);

        assertThat(subscription.getCancellationType())
                .isEqualTo(
                        CancellationType.OFFERING_UNDER_SUBSCRIBED
                );
    }

    @Test
    @DisplayName("CONFIRMED 청약은 모집 미달 보상을 시작하면 COMPENSATING으로 전환된다")
    void startsCompensationFromConfirmed() {
        // given
        Subscription subscription = createSubscription();

        subscription.markHoldSucceeded();
        subscription.confirm(Instant.now());

        // when
        subscription.startCompensation(
                CancellationType.OFFERING_UNDER_SUBSCRIBED
        );

        // then
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);

        assertThat(subscription.getCancellationType())
                .isEqualTo(
                        CancellationType.OFFERING_UNDER_SUBSCRIBED
                );
    }

    @Test
    @DisplayName("최종 상태의 청약은 보상 처리를 시작할 수 없다")
    void cannotStartCompensationFromFinalStatus() {
        // given
        Subscription subscription = createSubscription();

        ReflectionTestUtils.setField(
                subscription,
                "subscriptionStatus",
                SubscriptionStatus.CANCELLED
        );

        // when & then
        assertThatThrownBy(() ->
                subscription.startCompensation(
                        CancellationType.OFFERING_UNDER_SUBSCRIBED
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SubscriptionErrorCode
                                        .SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
                        )
                );
    }

    @Test
    @DisplayName("취소 유형이 없으면 보상 처리를 시작할 수 없다")
    void cannotStartCompensationWithoutCancellationType() {
        // given
        Subscription subscription = createSubscription();

        // when & then
        assertThatThrownBy(() ->
                subscription.startCompensation(null)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SubscriptionErrorCode
                                        .INVALID_SUBSCRIPTION_INPUT
                        )
                );
    }

    @Test
    @DisplayName("예약 시간이 만료된 PROCESSING 청약은 만료 보상을 시작한다")
    void startsExpirationCompensationAfterReservationExpires() {
        // given
        Subscription subscription = createSubscription();

        Instant expiredAt =
                subscription.getReservationExpiresAt().plusSeconds(1);

        // when
        subscription.startExpirationCompensation(expiredAt);

        // then
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);
        assertThat(subscription.getFailureCode())
                .isEqualTo("RESERVATION_EXPIRED");
        assertThat(subscription.getCancellationType()).isNull();
        assertThat(subscription.isQuantityReserved()).isTrue();
        assertThat(
                subscription.isReservationExpirationCompensation()
        ).isTrue();
    }

    @Test
    @DisplayName("예약 시간이 지나지 않은 청약은 만료 보상을 시작할 수 없다")
    void cannotStartExpirationCompensationBeforeReservationExpires() {
        // given
        Subscription subscription = createSubscription();

        Instant beforeExpiration =
                subscription.getReservationExpiresAt().minusSeconds(1);

        // when & then
        assertThatThrownBy(() ->
                subscription.startExpirationCompensation(
                        beforeExpiration
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SubscriptionErrorCode
                                        .SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
                        )
                );

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.PROCESSING);
        assertThat(subscription.getFailureCode()).isNull();
        assertThat(subscription.isQuantityReserved()).isTrue();
    }

    @Test
    @DisplayName("예약 만료 보상이 완료되면 수량 확보를 해제하고 REJECTED로 전환한다")
    void completesExpirationCompensationAsRejected() {
        // given
        Subscription subscription = createSubscription();

        subscription.startExpirationCompensation(
                subscription.getReservationExpiresAt()
        );

        assertThat(
                subscription.isReservationExpirationCompensation()
        ).isTrue();

        // when
        subscription.completeExpirationRejection();

        // then
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.REJECTED);
        assertThat(subscription.isQuantityReserved()).isFalse();
        assertThat(subscription.getFailureCode())
                .isEqualTo("RESERVATION_EXPIRED");
        assertThat(subscription.getCancellationType()).isNull();
        assertThat(subscription.getCancelledAt()).isNull();

        assertThat(
                subscription.isReservationExpirationCompensation()
        ).isFalse();
    }

    @Test
    @DisplayName("공모 취소 보상 중인 청약은 예약 만료 거절로 완료할 수 없다")
    void cannotCompleteOfferingCancellationAsExpirationRejection() {
        // given
        Subscription subscription = createSubscription();

        subscription.startCompensation(
                CancellationType.OFFERING_UNDER_SUBSCRIBED
        );

        // when & then
        assertThatThrownBy(
                subscription::completeExpirationRejection
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SubscriptionErrorCode
                                        .SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
                        )
                );

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);
        assertThat(subscription.isQuantityReserved()).isTrue();
        assertThat(subscription.getCancellationType())
                .isEqualTo(
                        CancellationType.OFFERING_UNDER_SUBSCRIBED
                );
    }

    private Subscription createSubscription() {
        return Subscription.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                10L,
                10_000L,
                Instant.now().plusSeconds(300)
        );
    }

    @Test
    @DisplayName("MANUAL_REVIEW 청약은 관리자 보상 요청으로 COMPENSATING 상태로 전환된다")
    void restartsCompensationFromManualReview() {
        // given
        Subscription subscription = createSubscription();

        subscription.startCompensation(
                CancellationType.OFFERING_ADMIN_CANCELLED
        );
        subscription.requireManualReview(
                "WALLET_COMPENSATION_FAILED"
        );

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.MANUAL_REVIEW);

        // when
        subscription.restartCompensation();

        // then
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);

        assertThat(subscription.getCancellationType())
                .isEqualTo(
                        CancellationType.OFFERING_ADMIN_CANCELLED
                );

        assertThat(subscription.getFailureCode())
                .isEqualTo("WALLET_COMPENSATION_FAILED");

        assertThat(subscription.isQuantityReserved()).isTrue();
    }

    @Test
    @DisplayName("MANUAL_REVIEW 상태가 아니면 관리자 보상을 다시 시작할 수 없다")
    void cannotRestartCompensationFromNonManualReviewStatus() {
        // given
        Subscription subscription = createSubscription();

        // when & then
        assertThatThrownBy(subscription::restartCompensation)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SubscriptionErrorCode
                                        .SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
                        )
                );

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.PROCESSING);

        assertThat(subscription.isQuantityReserved()).isTrue();
    }

    @Test
    @DisplayName("예약 만료 보상을 다시 시작해도 기존 보상 원인을 유지한다")
    void preservesExpirationContextWhenRestartingCompensation() {
        // given
        Subscription subscription = createSubscription();

        subscription.startExpirationCompensation(
                subscription.getReservationExpiresAt()
        );
        subscription.requireManualReview(
                "LATE_WALLET_HOLD_SUCCEEDED"
        );

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.MANUAL_REVIEW);

        /*
         * requireManualReview는 기존 failureCode가 있으면
         * 새로운 사유로 덮어쓰지 않는다.
         */
        assertThat(subscription.getFailureCode())
                .isEqualTo("RESERVATION_EXPIRED");

        // when
        subscription.restartCompensation();

        // then
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);

        assertThat(subscription.getFailureCode())
                .isEqualTo("RESERVATION_EXPIRED");

        assertThat(subscription.getCancellationType()).isNull();
        assertThat(subscription.isQuantityReserved()).isTrue();

        assertThat(
                subscription.isReservationExpirationCompensation()
        ).isTrue();
    }
}
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
    @DisplayName("청약 확정 시 Holding 배정 후처리를 PENDING으로 시작한다")
    void startsHoldingAllocationWhenConfirmed() {
        // given
        Subscription subscription = createSubscription();

        // when
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
                                ((BusinessException) exception)
                                        .getErrorCode()
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

        ReflectionTestUtils.setField(
                subscription,
                "subscriptionStatus",
                SubscriptionStatus.CONFIRMED
        );

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

    private Subscription createSubscription() {
        return Subscription.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                10L,
                10_000L,
                Instant.now().plusSeconds(300)
        );
    }
}
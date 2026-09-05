package com.moneykk.moneytown.offering.subscription.domain.entity;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

class SubscriptionTest {

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
                .isEqualTo(CancellationType.OFFERING_UNDER_SUBSCRIBED);
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
                .isEqualTo(CancellationType.OFFERING_UNDER_SUBSCRIBED);
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
                                ((BusinessException) exception).getErrorCode()
                        ).isEqualTo(
                                SubscriptionErrorCode.SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
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
                                ((BusinessException) exception).getErrorCode()
                        ).isEqualTo(
                                SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
                        )
                );
    }
}
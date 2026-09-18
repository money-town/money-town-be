package com.moneykk.moneytown.offering.subscription.domain.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionRequestHasherTest {

    private final SubscriptionRequestHasher subscriptionRequestHasher =
            new SubscriptionRequestHasher();

    @Test
    @DisplayName("offeringId와 quantity가 같으면 동일한 요청 해시를 생성한다")
    void hashIsDeterministicForSameInput() {
        // given
        UUID offeringId = UUID.randomUUID();
        Long quantity = 10L;

        // when
        String first = subscriptionRequestHasher.hash(offeringId, quantity);
        String second = subscriptionRequestHasher.hash(offeringId, quantity);

        // then
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
    }

    @Test
    @DisplayName("quantity가 다르면 다른 요청 해시를 생성한다")
    void hashDiffersWhenQuantityDiffers() {
        // given
        UUID offeringId = UUID.randomUUID();

        // when
        String first = subscriptionRequestHasher.hash(offeringId, 10L);
        String second = subscriptionRequestHasher.hash(offeringId, 20L);

        // then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("offeringId가 없으면 청약 해시 생성에 실패한다")
    void rejectsNullOfferingId() {
        // when & then
        assertThatThrownBy(() -> subscriptionRequestHasher.hash(null, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT);
    }

    @Test
    @DisplayName("quantity가 없으면 청약 해시 생성에 실패한다")
    void rejectsNullQuantity() {
        // when & then
        assertThatThrownBy(() ->
                subscriptionRequestHasher.hash(UUID.randomUUID(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SubscriptionErrorCode.INVALID_SUBSCRIPTION_QUANTITY);
    }

    @Test
    @DisplayName("subscriptionId가 같으면 동일한 보상 요청 해시를 생성한다")
    void hashCompensationIsDeterministic() {
        // given
        UUID subscriptionId = UUID.randomUUID();

        // when
        String first = subscriptionRequestHasher.hashCompensation(subscriptionId);
        String second = subscriptionRequestHasher.hashCompensation(subscriptionId);

        // then
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("subscriptionId가 없으면 보상 요청 해시 생성에 실패한다")
    void rejectsNullSubscriptionIdForCompensation() {
        // when & then
        assertThatThrownBy(() ->
                subscriptionRequestHasher.hashCompensation(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT);
    }

    @Test
    @DisplayName("subscriptionId가 같으면 동일한 재처리 요청 해시를 생성한다")
    void hashRetryIsDeterministic() {
        // given
        UUID subscriptionId = UUID.randomUUID();

        // when
        String first = subscriptionRequestHasher.hashRetry(subscriptionId);
        String second = subscriptionRequestHasher.hashRetry(subscriptionId);

        // then
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("subscriptionId가 없으면 재처리 요청 해시 생성에 실패한다")
    void rejectsNullSubscriptionIdForRetry() {
        // when & then
        assertThatThrownBy(() ->
                subscriptionRequestHasher.hashRetry(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT);
    }

    @Test
    @DisplayName("작업 종류가 다르면 같은 subscriptionId라도 보상과 재처리 요청 해시가 다르다")
    void compensationAndRetryHashesDifferForSameSubscriptionId() {
        // given
        UUID subscriptionId = UUID.randomUUID();

        // when
        String compensationHash =
                subscriptionRequestHasher.hashCompensation(subscriptionId);
        String retryHash =
                subscriptionRequestHasher.hashRetry(subscriptionId);

        // then
        assertThat(compensationHash).isNotEqualTo(retryHash);
    }
}

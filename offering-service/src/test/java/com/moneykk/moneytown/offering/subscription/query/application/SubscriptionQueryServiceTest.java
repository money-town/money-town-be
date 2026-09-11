package com.moneykk.moneytown.offering.subscription.query.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.query.dto.response.SubscriptionDetailResponse;
import com.moneykk.moneytown.offering.subscription.query.repository.SubscriptionQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionQueryServiceTest {

    @Mock
    private SubscriptionQueryRepository subscriptionQueryRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private Subscription subscription;

    @InjectMocks
    private SubscriptionQueryService subscriptionQueryService;

    @Test
    @DisplayName("INVESTOR는 본인의 청약 상세를 조회할 수 있다")
    void allowsOwnerInvestorToReadSubscription() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID investorId = UUID.randomUUID();

        when(subscription.getUserId())
                .thenReturn(investorId);

        when(subscription.getSubscriptionId())
                .thenReturn(subscriptionId);

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        // when
        SubscriptionDetailResponse response =
                subscriptionQueryService.getSubscriptionDetail(
                        subscriptionId,
                        investorId,
                        "INVESTOR"
                );

        // then
        assertThat(response.subscriptionId())
                .isEqualTo(subscriptionId);
    }

    @Test
    @DisplayName("INVESTOR는 다른 사용자의 청약 상세를 조회할 수 없다")
    void deniesNonOwnerInvestor() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherInvestorId = UUID.randomUUID();

        when(subscription.getUserId())
                .thenReturn(ownerId);

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionQueryService.getSubscriptionDetail(
                        subscriptionId,
                        otherInvestorId,
                        "INVESTOR"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.SUBSCRIPTION_ACCESS_DENIED
                );
    }

    @Test
    @DisplayName("ADMIN은 다른 사용자의 청약 상세를 조회할 수 있다")
    void allowsAdminToReadSubscription() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(subscription.getUserId())
                .thenReturn(ownerId);

        when(subscription.getSubscriptionId())
                .thenReturn(subscriptionId);

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        // when
        SubscriptionDetailResponse response =
                subscriptionQueryService.getSubscriptionDetail(
                        subscriptionId,
                        adminId,
                        "ADMIN"
                );

        // then
        assertThat(response.subscriptionId())
                .isEqualTo(subscriptionId);
    }

    @Test
    @DisplayName("ISSUER는 청약 소유자 ID와 같아도 청약 상세를 조회할 수 없다")
    void deniesIssuerEvenWhenUserIdMatchesOwner() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(subscription.getUserId())
                .thenReturn(userId);

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionQueryService.getSubscriptionDetail(
                        subscriptionId,
                        userId,
                        "ISSUER"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.SUBSCRIPTION_ACCESS_DENIED
                );
    }

    @Test
    @DisplayName("역할이 없으면 본인의 청약도 조회할 수 없다")
    void deniesAccessWhenRoleIsNull() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(subscription.getUserId())
                .thenReturn(userId);

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionQueryService.getSubscriptionDetail(
                        subscriptionId,
                        userId,
                        null
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.SUBSCRIPTION_ACCESS_DENIED
                );
    }

    @Test
    @DisplayName("존재하지 않거나 삭제된 청약은 조회할 수 없다")
    void throwsNotFoundWhenSubscriptionDoesNotExist() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID investorId = UUID.randomUUID();

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionQueryService.getSubscriptionDetail(
                        subscriptionId,
                        investorId,
                        "INVESTOR"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                );
    }
}
package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionLifecycleMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionConfirmationItemTransactionServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionEventPublisher subscriptionEventPublisher;

    @Mock
    private SubscriptionLifecycleMetrics subscriptionLifecycleMetrics;

    @InjectMocks
    private SubscriptionConfirmationItemTransactionService service;

    @Test
    @DisplayName("확정 가능한 청약 한 건을 독립적으로 확정하고 이벤트를 저장한다")
    void confirmsSingleSubscription() {
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Offering offering = org.mockito.Mockito.mock(Offering.class);
        Subscription subscription = org.mockito.Mockito.mock(Subscription.class);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));
        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.SOLD_OUT);
        when(offering.getRemainingQuantity()).thenReturn(0L);
        when(offering.getAssetId()).thenReturn(assetId);
        when(subscriptionRepository.findByIdForUpdate(subscriptionId))
                .thenReturn(Optional.of(subscription));
        when(subscription.getOfferingId()).thenReturn(offeringId);
        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.HOLD_SUCCEEDED);
        when(subscription.isQuantityReserved()).thenReturn(true);

        boolean confirmed = service.confirm(offeringId, subscriptionId);

        assertThat(confirmed).isTrue();
        verify(subscription).confirm(any(Instant.class));
        verify(subscriptionEventPublisher).publishConfirmed(
                subscription,
                assetId,
                offeringId.toString()
        );
        verify(subscriptionLifecycleMetrics).publishOutcome(
                eq(subscription),
                eq(SubscriptionLifecycleMetrics.Result.CONFIRMED),
                any(Instant.class)
        );
        verify(subscriptionRepository).flush();
    }

    @Test
    @DisplayName("공모가 최종 확정 가능한 상태가 아니면 청약을 조회하지 않는다")
    void doesNotConfirmWhenOfferingIsNotFinalizable() {
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Offering offering = org.mockito.Mockito.mock(Offering.class);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));
        when(offering.getOfferingStatus()).thenReturn(OfferingStatus.OPEN);

        boolean confirmed = service.confirm(offeringId, subscriptionId);

        assertThat(confirmed).isFalse();
        verifyNoInteractions(
                subscriptionRepository,
                subscriptionEventPublisher,
                subscriptionLifecycleMetrics
        );
    }

    @Test
    @DisplayName("다른 공모의 청약은 확정하지 않는다")
    void doesNotConfirmSubscriptionFromAnotherOffering() {
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Offering offering = org.mockito.Mockito.mock(Offering.class);
        Subscription subscription = org.mockito.Mockito.mock(Subscription.class);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));
        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.CLOSED);
        when(offering.getRemainingQuantity()).thenReturn(0L);
        when(subscriptionRepository.findByIdForUpdate(subscriptionId))
                .thenReturn(Optional.of(subscription));
        when(subscription.getOfferingId()).thenReturn(UUID.randomUUID());

        boolean confirmed = service.confirm(offeringId, subscriptionId);

        assertThat(confirmed).isFalse();
        verify(subscription, never()).confirm(any(Instant.class));
        verify(subscriptionRepository, never()).flush();
        verifyNoInteractions(
                subscriptionEventPublisher,
                subscriptionLifecycleMetrics
        );
    }

    @Test
    @DisplayName("예약 수량이 없는 청약은 확정하지 않는다")
    void doesNotConfirmSubscriptionWithoutReservedQuantity() {
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Offering offering = org.mockito.Mockito.mock(Offering.class);
        Subscription subscription = org.mockito.Mockito.mock(Subscription.class);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));
        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.SOLD_OUT);
        when(offering.getRemainingQuantity()).thenReturn(0L);
        when(subscriptionRepository.findByIdForUpdate(subscriptionId))
                .thenReturn(Optional.of(subscription));
        when(subscription.getOfferingId()).thenReturn(offeringId);
        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.HOLD_SUCCEEDED);
        when(subscription.isQuantityReserved()).thenReturn(false);

        boolean confirmed = service.confirm(offeringId, subscriptionId);

        assertThat(confirmed).isFalse();
        verify(subscription, never()).confirm(any(Instant.class));
        verify(subscriptionRepository, never()).flush();
        verifyNoInteractions(
                subscriptionEventPublisher,
                subscriptionLifecycleMetrics
        );
    }
}

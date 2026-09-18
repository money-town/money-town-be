package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
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
class SubscriptionConfirmationManualReviewServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionLifecycleMetrics subscriptionLifecycleMetrics;

    @InjectMocks
    private SubscriptionConfirmationManualReviewService service;

    @Test
    @DisplayName("자동 확정에 실패한 청약을 수동 확인 상태로 전환한다")
    void marksSubscriptionForManualReview() {
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
        when(subscription.isQuantityReserved()).thenReturn(true);

        boolean marked = service.markForManualReview(
                offeringId,
                subscriptionId
        );

        assertThat(marked).isTrue();
        verify(subscription).requireManualReview(
                SubscriptionConfirmationManualReviewService
                        .CONFIRMATION_BATCH_FAILURE_CODE
        );
        verify(subscriptionLifecycleMetrics).publishOutcome(
                eq(subscription),
                eq(SubscriptionLifecycleMetrics.Result.MANUAL_REVIEW),
                any(Instant.class)
        );
        verify(subscriptionRepository).flush();
    }

    @Test
    @DisplayName("공모가 최종 확정 가능한 상태가 아니면 수동 확인 처리하지 않는다")
    void doesNotMarkWhenOfferingIsNotFinalizable() {
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Offering offering = org.mockito.Mockito.mock(Offering.class);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));
        when(offering.getOfferingStatus()).thenReturn(OfferingStatus.OPEN);

        boolean marked = service.markForManualReview(
                offeringId,
                subscriptionId
        );

        assertThat(marked).isFalse();
        verifyNoInteractions(
                subscriptionRepository,
                subscriptionLifecycleMetrics
        );
    }

    @Test
    @DisplayName("이미 처리된 청약은 수동 확인 상태로 변경하지 않는다")
    void doesNotMarkAlreadyProcessedSubscription() {
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
        when(subscription.getOfferingId()).thenReturn(offeringId);
        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.CONFIRMED);

        boolean marked = service.markForManualReview(
                offeringId,
                subscriptionId
        );

        assertThat(marked).isFalse();
        verify(subscription, never()).requireManualReview(any(String.class));
        verify(subscriptionRepository, never()).flush();
        verifyNoInteractions(subscriptionLifecycleMetrics);
    }

    @Test
    @DisplayName("다른 공모의 청약은 수동 확인 상태로 변경하지 않는다")
    void doesNotMarkSubscriptionFromAnotherOffering() {
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
        when(subscription.getOfferingId()).thenReturn(UUID.randomUUID());

        boolean marked = service.markForManualReview(
                offeringId,
                subscriptionId
        );

        assertThat(marked).isFalse();
        verify(subscription, never()).requireManualReview(any(String.class));
        verify(subscriptionRepository, never()).flush();
        verifyNoInteractions(subscriptionLifecycleMetrics);
    }
}

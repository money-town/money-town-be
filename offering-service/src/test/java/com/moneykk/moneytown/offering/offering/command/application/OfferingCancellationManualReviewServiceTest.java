package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingCancellationManualReviewServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private OfferingCancellationManualReviewService service;

    @Test
    @DisplayName("자동 보상 시작에 실패한 청약을 공모 중단 수동 확인 상태로 전환한다")
    void marksFailedSubscriptionForManualReview() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
        );

        Subscription subscription =
                org.mockito.Mockito.mock(
                        Subscription.class
                );

        when(
                offeringRepository.findByIdForUpdate(offeringId)
        ).thenReturn(Optional.of(offering));

        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.CANCELLING);

        when(offering.getCancellationType())
                .thenReturn(
                        com.moneykk.moneytown.offering.offering
                                .domain.entity.CancellationType
                                .ADMIN_CANCELLED
                );

        when(
                subscriptionRepository
                        .findByIdForUpdate(subscriptionId)
        ).thenReturn(Optional.of(subscription));

        when(subscription.getOfferingId())
                .thenReturn(offeringId);

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.PROCESSING);

        // when
        boolean marked = service.markForManualReview(
                offeringId,
                subscriptionId
        );

        // then
        assertThat(marked).isTrue();

        verify(subscription).startCompensation(
                CancellationType.OFFERING_ADMIN_CANCELLED
        );

        verify(subscription).requireManualReview(
                OfferingCancellationManualReviewService
                        .CANCELLATION_BATCH_FAILURE_CODE
        );
    }

    @Test
    @DisplayName("모집 미달 보상 실패 청약도 수동 확인 상태로 전환한다")
    void marksUnderSubscribedSubscriptionForManualReview() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
        );

        Subscription subscription =
                org.mockito.Mockito.mock(
                        Subscription.class
                );

        when(
                offeringRepository.findByIdForUpdate(offeringId)
        ).thenReturn(Optional.of(offering));

        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.CANCELLING);

        when(offering.getCancellationType())
                .thenReturn(
                        com.moneykk.moneytown.offering.offering
                                .domain.entity.CancellationType
                                .UNDER_SUBSCRIBED
                );

        when(
                subscriptionRepository
                        .findByIdForUpdate(subscriptionId)
        ).thenReturn(Optional.of(subscription));

        when(subscription.getOfferingId())
                .thenReturn(offeringId);

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.PROCESSING);

        // when
        boolean marked = service.markForManualReview(
                offeringId,
                subscriptionId
        );

        // then
        assertThat(marked).isTrue();

        verify(subscription).startCompensation(
                CancellationType.OFFERING_UNDER_SUBSCRIBED
        );

        verify(subscription).requireManualReview(
                OfferingCancellationManualReviewService
                        .CANCELLATION_BATCH_FAILURE_CODE
        );
    }

    @Test
    @DisplayName("이미 처리 중인 청약은 다시 수동 확인 상태로 변경하지 않는다")
    void doesNotMarkAlreadyProcessedSubscription() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
        );

        Subscription subscription =
                org.mockito.Mockito.mock(
                        Subscription.class
                );

        when(
                offeringRepository.findByIdForUpdate(offeringId)
        ).thenReturn(Optional.of(offering));

        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.CANCELLING);

        when(offering.getCancellationType())
                .thenReturn(
                        com.moneykk.moneytown.offering.offering
                                .domain.entity.CancellationType
                                .ADMIN_CANCELLED
                );

        when(
                subscriptionRepository
                        .findByIdForUpdate(subscriptionId)
        ).thenReturn(Optional.of(subscription));

        when(subscription.getOfferingId())
                .thenReturn(offeringId);

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.COMPENSATING);

        // when
        boolean marked = service.markForManualReview(
                offeringId,
                subscriptionId
        );

        // then
        assertThat(marked).isFalse();

        verify(
                subscription,
                never()
        ).startCompensation(any(CancellationType.class));

        verify(
                subscription,
                never()
        ).requireManualReview(any(String.class));
    }

    @Test
    @DisplayName("다른 공모의 청약은 수동 확인 상태로 변경하지 않는다")
    void doesNotMarkSubscriptionFromAnotherOffering() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID anotherOfferingId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
        );

        Subscription subscription =
                org.mockito.Mockito.mock(
                        Subscription.class
                );

        when(
                offeringRepository.findByIdForUpdate(offeringId)
        ).thenReturn(Optional.of(offering));

        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.CANCELLING);

        when(offering.getCancellationType())
                .thenReturn(
                        com.moneykk.moneytown.offering.offering
                                .domain.entity.CancellationType
                                .ADMIN_CANCELLED
                );

        when(
                subscriptionRepository
                        .findByIdForUpdate(subscriptionId)
        ).thenReturn(Optional.of(subscription));

        when(subscription.getOfferingId())
                .thenReturn(anotherOfferingId);

        // when
        boolean marked = service.markForManualReview(
                offeringId,
                subscriptionId
        );

        // then
        assertThat(marked).isFalse();

        verify(
                subscription,
                never()
        ).startCompensation(any(CancellationType.class));

        verify(
                subscription,
                never()
        ).requireManualReview(any(String.class));
    }
}

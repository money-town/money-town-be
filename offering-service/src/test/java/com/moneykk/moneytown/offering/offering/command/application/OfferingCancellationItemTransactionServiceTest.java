package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
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
class OfferingCancellationItemTransactionServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionCompensationRepository
            subscriptionCompensationRepository;

    @Mock
    private SubscriptionEventPublisher subscriptionEventPublisher;

    @InjectMocks
    private OfferingCancellationItemTransactionService service;

    @Test
    @DisplayName("관리자 중단 공모의 보상 대상 청약 한 건을 독립 처리한다")
    void compensatesSingleSubscription() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
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

        when(offering.getAssetId())
                .thenReturn(assetId);

        when(
                subscriptionRepository
                        .findByIdForUpdate(subscriptionId)
        ).thenReturn(Optional.of(subscription));

        when(subscription.getOfferingId())
                .thenReturn(offeringId);

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.PROCESSING);

        when(subscription.getSubscriptionId())
                .thenReturn(subscriptionId);

        // when
        boolean compensated = service.compensate(
                offeringId,
                subscriptionId
        );

        // then
        assertThat(compensated).isTrue();

        verify(subscription).startCompensation(
                CancellationType.OFFERING_ADMIN_CANCELLED
        );

        verify(
                subscriptionCompensationRepository
        ).save(any(SubscriptionCompensation.class));

        verify(subscriptionEventPublisher)
                .publishCompensationRequested(
                        subscription,
                        assetId,
                        offeringId.toString()
                );

        verify(
                subscriptionCompensationRepository
        ).flush();
    }

    @Test
    @DisplayName("관리자 중단 공모가 아니면 청약을 조회하지 않는다")
    void doesNotCompensateNonAdminCancellationOffering() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
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

        // when
        boolean compensated = service.compensate(
                offeringId,
                subscriptionId
        );

        // then
        assertThat(compensated).isFalse();

        verifyNoInteractions(
                subscriptionRepository,
                subscriptionCompensationRepository,
                subscriptionEventPublisher
        );
    }

    @Test
    @DisplayName("청약이 이미 처리 대상 상태가 아니면 중복 보상을 생성하지 않는다")
    void doesNotCompensateAlreadyProcessedSubscription() {
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
        boolean compensated = service.compensate(
                offeringId,
                subscriptionId
        );

        // then
        assertThat(compensated).isFalse();

        verify(
                subscription,
                never()
        ).startCompensation(any(CancellationType.class));

        verifyNoInteractions(
                subscriptionCompensationRepository,
                subscriptionEventPublisher
        );
    }

    @Test
    @DisplayName("다른 공모의 청약이면 보상을 시작하지 않는다")
    void doesNotCompensateSubscriptionFromAnotherOffering() {
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
        boolean compensated = service.compensate(
                offeringId,
                subscriptionId
        );

        // then
        assertThat(compensated).isFalse();

        verify(
                subscription,
                never()
        ).startCompensation(any(CancellationType.class));

        verifyNoInteractions(
                subscriptionCompensationRepository,
                subscriptionEventPublisher
        );
    }
}
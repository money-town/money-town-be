package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCancellationResponse;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;

import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class OfferingStatusTransitionServiceTest {

    @Mock
    private OfferingRepository offeringRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionEventPublisher subscriptionEventPublisher;
    @Mock
    private SubscriptionCompensationRepository subscriptionCompensationRepository;

    @Mock
    private OfferingCompensationCompletionService offeringCompensationCompletionService;
    @InjectMocks
    private OfferingStatusTransitionService offeringStatusTransitionService;

    @Test
    @DisplayName("SCHEDULED 공모의 OPEN 전환 건수를 반환한다")
    void opensScheduledOfferings() {
        when(offeringRepository.openScheduledOfferings(
                JpaAuditingConfig.SYSTEM_USER_ID
        )).thenReturn(3);

        int result =
                offeringStatusTransitionService.openScheduledOfferings();

        assertThat(result).isEqualTo(3);

        verify(offeringRepository)
                .openScheduledOfferings(
                        JpaAuditingConfig.SYSTEM_USER_ID
                );
    }

    @Test
    @DisplayName("SOLD_OUT 공모의 CLOSED 전환 건수를 반환한다")
    void closesSoldOutOfferings() {
        when(offeringRepository.closeSoldOutOfferings(
                JpaAuditingConfig.SYSTEM_USER_ID
        )).thenReturn(2);

        int result =
                offeringStatusTransitionService.closeSoldOutOfferings();

        assertThat(result).isEqualTo(2);

        verify(offeringRepository)
                .closeSoldOutOfferings(
                        JpaAuditingConfig.SYSTEM_USER_ID
                );
    }

    @Test
    @DisplayName("모집 미달 공모와 보상 대상 청약을 보상 진행 상태로 전환한다")
    void startsUnderSubscribedCancellations() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID processingSubscriptionId = UUID.randomUUID();
        UUID confirmedSubscriptionId = UUID.randomUUID();

        Offering offering = mock(Offering.class);
        Subscription processingSubscription = mock(Subscription.class);
        Subscription confirmedSubscription = mock(Subscription.class);


        when(processingSubscription.getSubscriptionId()).thenReturn(processingSubscriptionId);
        when(confirmedSubscription.getSubscriptionId()).thenReturn(confirmedSubscriptionId);
        when(offering.getOfferingId()).thenReturn(offeringId);
        when(offering.getAssetId()).thenReturn(assetId);
        when(offeringRepository.findUnderSubscribedOfferingsForUpdate(any(),any())).thenReturn(List.of(offering));
        when(subscriptionRepository.findAllByOfferingIdAndSubscriptionStatusInAndIsDeletedFalse(
                        eq(offeringId),
                        eq(List.of(SubscriptionStatus.PROCESSING, SubscriptionStatus.CONFIRMED))
                ))
                .thenReturn(List.of(processingSubscription, confirmedSubscription));

        // when
        int result = offeringStatusTransitionService.startUnderSubscribedCancellations();

        // then
        assertThat(result).isEqualTo(1);

        verify(offering)
                .startUnderSubscribedCancellation();

        verify(processingSubscription)
                .startCompensation(
                        CancellationType.OFFERING_UNDER_SUBSCRIBED
                );

        verify(confirmedSubscription)
                .startCompensation(
                        CancellationType.OFFERING_UNDER_SUBSCRIBED
                );

        ArgumentCaptor<String> correlationIdCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(subscriptionEventPublisher)
                .publishCompensationRequested(
                        eq(processingSubscription),
                        eq(assetId),
                        correlationIdCaptor.capture()
                );

        String correlationId = correlationIdCaptor.getValue();

        assertThat(correlationId).isNotBlank();

        verify(subscriptionEventPublisher)
                .publishCompensationRequested(
                        eq(confirmedSubscription),
                        eq(assetId),
                        eq(correlationId)
                );

        ArgumentCaptor<SubscriptionCompensation> compensationCaptor =
                ArgumentCaptor.forClass(SubscriptionCompensation.class);

        verify(subscriptionCompensationRepository, times(2))
                .save(compensationCaptor.capture());

        assertThat(compensationCaptor.getAllValues())
                .extracting(SubscriptionCompensation::getSubscriptionId)
                .containsExactlyInAnyOrder(
                        processingSubscriptionId,
                        confirmedSubscriptionId
                );

        verify(offeringCompensationCompletionService)
                .completeIfReady(offeringId);
    }

    @Test
    @DisplayName("보상 대상 청약이 없는 모집 미달 공모도 취소 완료 여부를 확인한다")
    void checksCompletionWhenNoCompensatableSubscriptionsExist() {
        UUID offeringId = UUID.randomUUID();
        Offering offering = mock(Offering.class);

        when(offering.getOfferingId()).thenReturn(offeringId);

        when(offeringRepository.findUnderSubscribedOfferingsForUpdate(
                any(),
                any()
        )).thenReturn(List.of(offering));

        when(subscriptionRepository
                .findAllByOfferingIdAndSubscriptionStatusInAndIsDeletedFalse(
                        eq(offeringId),
                        eq(List.of(
                                SubscriptionStatus.PROCESSING,
                                SubscriptionStatus.CONFIRMED
                        ))
                )
        ).thenReturn(List.of());

        int result =
                offeringStatusTransitionService.startUnderSubscribedCancellations();

        assertThat(result).isEqualTo(1);

        verify(offering).startUnderSubscribedCancellation();

        verify(offeringCompensationCompletionService)
                .completeIfReady(offeringId);

        verifyNoInteractions(
                subscriptionCompensationRepository,
                subscriptionEventPublisher
        );
    }

    @Test
    @DisplayName("보상 대상 청약이 없는 SCHEDULED 공모는 관리자 중단 요청에서 즉시 취소된다")
    void cancelsScheduledOfferingImmediatelyByAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        Offering offering = mock(Offering.class);

        when(offering.getOfferingId())
                .thenReturn(offeringId);

        /*
         * 완료 서비스가 미해결 청약이 없음을 확인하고
         * 공모를 CANCELLED로 변경한 이후의 상태를 표현한다.
         */
        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.CANCELLED);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        when(subscriptionRepository
                .findAllByOfferingIdAndSubscriptionStatusInAndIsDeletedFalse(
                        offeringId,
                        List.of(
                                SubscriptionStatus.PROCESSING,
                                SubscriptionStatus.CONFIRMED
                        )
                ))
                .thenReturn(List.of());

        // when
        OfferingCancellationResponse response =
                offeringStatusTransitionService.cancelByAdmin(
                        offeringId,
                        correlationId
                );

        // then
        assertThat(response.offeringId())
                .isEqualTo(offeringId);

        assertThat(response.offeringStatus())
                .isEqualTo(OfferingStatus.CANCELLED);

        verify(offering)
                .startAdminCancellation();

        verify(offeringCompensationCompletionService)
                .completeIfReady(offeringId);

        verifyNoInteractions(
                subscriptionCompensationRepository,
                subscriptionEventPublisher
        );
    }

    @Test
    @DisplayName("보상 대상 청약이 있는 OPEN 공모는 관리자 중단 후 보상 요청을 저장한다")
    void startsCompensationWhenAdminCancelsOpenOffering() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        Offering offering = mock(Offering.class);
        Subscription subscription = mock(Subscription.class);

        when(offering.getOfferingId())
                .thenReturn(offeringId);

        when(offering.getAssetId())
                .thenReturn(assetId);

        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.CANCELLING);

        when(subscription.getSubscriptionId())
                .thenReturn(subscriptionId);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        when(subscriptionRepository
                .findAllByOfferingIdAndSubscriptionStatusInAndIsDeletedFalse(
                        offeringId,
                        List.of(
                                SubscriptionStatus.PROCESSING,
                                SubscriptionStatus.CONFIRMED
                        )
                ))
                .thenReturn(List.of(subscription));

        // when
        OfferingCancellationResponse response =
                offeringStatusTransitionService.cancelByAdmin(
                        offeringId,
                        correlationId
                );

        // then
        assertThat(response.offeringId())
                .isEqualTo(offeringId);

        assertThat(response.offeringStatus())
                .isEqualTo(OfferingStatus.CANCELLING);

        verify(offering)
                .startAdminCancellation();

        verify(subscription)
                .startCompensation(
                        CancellationType.OFFERING_ADMIN_CANCELLED
                );

        ArgumentCaptor<SubscriptionCompensation> compensationCaptor =
                ArgumentCaptor.forClass(
                        SubscriptionCompensation.class
                );

        verify(subscriptionCompensationRepository)
                .save(compensationCaptor.capture());

        assertThat(compensationCaptor.getValue().getSubscriptionId())
                .isEqualTo(subscriptionId);

        verify(subscriptionEventPublisher)
                .publishCompensationRequested(
                        subscription,
                        assetId,
                        correlationId
                );

        verify(offeringCompensationCompletionService)
                .completeIfReady(offeringId);
    }

    @Test
    @DisplayName("존재하지 않는 공모는 관리자가 중단할 수 없다")
    void cannotCancelMissingOfferingByAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                offeringStatusTransitionService.cancelByAdmin(
                        offeringId,
                        correlationId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                OfferingErrorCode.OFFERING_NOT_FOUND
                        )
                );

        verifyNoInteractions(
                subscriptionRepository,
                subscriptionCompensationRepository,
                subscriptionEventPublisher,
                offeringCompensationCompletionService
        );
    }
}
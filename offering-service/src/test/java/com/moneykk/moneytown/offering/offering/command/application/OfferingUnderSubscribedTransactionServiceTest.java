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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingUnderSubscribedTransactionServiceTest {

    private static final List<SubscriptionStatus>
            COMPENSATABLE_STATUSES = List.of(
            SubscriptionStatus.PROCESSING,
            SubscriptionStatus.HOLD_SUCCEEDED,
            SubscriptionStatus.CONFIRMED
    );

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionCompensationRepository
            subscriptionCompensationRepository;

    @Mock
    private SubscriptionEventPublisher subscriptionEventPublisher;

    @Mock
    private OfferingCompensationCompletionService
            offeringCompensationCompletionService;

    @InjectMocks
    private OfferingUnderSubscribedTransactionService
            offeringUnderSubscribedTransactionService;

    @Test
    @DisplayName("모집 미달 공모와 보상 대상 청약을 보상 진행 상태로 전환한다")
    void startsUnderSubscribedCancellation() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        UUID processingSubscriptionId = UUID.randomUUID();
        UUID holdSucceededSubscriptionId = UUID.randomUUID();
        UUID confirmedSubscriptionId = UUID.randomUUID();

        Instant now = Instant.now();

        Offering offering = mock(Offering.class);

        Subscription processingSubscription =
                mock(Subscription.class);
        Subscription holdSucceededSubscription =
                mock(Subscription.class);
        Subscription confirmedSubscription =
                mock(Subscription.class);

        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.OPEN);
        when(offering.getEndAt())
                .thenReturn(now.minusSeconds(1));
        when(offering.getRemainingQuantity())
                .thenReturn(100L);
        when(offering.getAssetId())
                .thenReturn(assetId);

        when(processingSubscription.getSubscriptionId())
                .thenReturn(processingSubscriptionId);
        when(holdSucceededSubscription.getSubscriptionId())
                .thenReturn(holdSucceededSubscriptionId);
        when(confirmedSubscription.getSubscriptionId())
                .thenReturn(confirmedSubscriptionId);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        when(subscriptionRepository
                .findAllByOfferingIdAndSubscriptionStatusInAndIsDeletedFalse(
                        offeringId,
                        COMPENSATABLE_STATUSES
                ))
                .thenReturn(List.of(
                        processingSubscription,
                        holdSucceededSubscription,
                        confirmedSubscription
                ));

        // when
        boolean result =
                offeringUnderSubscribedTransactionService
                        .startUnderSubscribedCancellation(
                                offeringId,
                                now
                        );

        // then
        assertThat(result).isTrue();

        /*
         * 동시 처리의 잠금 순서를
         * Offering → Subscription으로 유지하는지 확인한다.
         */
        InOrder lockOrder = inOrder(
                offeringRepository,
                subscriptionRepository
        );

        lockOrder.verify(offeringRepository)
                .findByIdForUpdate(offeringId);

        lockOrder.verify(subscriptionRepository)
                .findAllByOfferingIdAndSubscriptionStatusInAndIsDeletedFalse(
                        offeringId,
                        COMPENSATABLE_STATUSES
                );

        verify(offering)
                .startUnderSubscribedCancellation();

        verify(processingSubscription)
                .startCompensation(
                        CancellationType.OFFERING_UNDER_SUBSCRIBED
                );

        verify(holdSucceededSubscription)
                .startCompensation(
                        CancellationType.OFFERING_UNDER_SUBSCRIBED
                );

        verify(confirmedSubscription)
                .startCompensation(
                        CancellationType.OFFERING_UNDER_SUBSCRIBED
                );

        /*
         * 세 청약에 생성된 보상 정보가 각각 올바른
         * subscriptionId를 사용하는지 확인한다.
         */
        ArgumentCaptor<SubscriptionCompensation>
                compensationCaptor =
                ArgumentCaptor.forClass(
                        SubscriptionCompensation.class
                );

        verify(subscriptionCompensationRepository, times(3))
                .save(compensationCaptor.capture());

        assertThat(compensationCaptor.getAllValues())
                .extracting(
                        SubscriptionCompensation::getSubscriptionId
                )
                .containsExactlyInAnyOrder(
                        processingSubscriptionId,
                        holdSucceededSubscriptionId,
                        confirmedSubscriptionId
                );

        /*
         * 공모에 포함된 모든 청약 이벤트는
         * 같은 correlationId를 사용해야 한다.
         */
        ArgumentCaptor<Subscription> subscriptionCaptor =
                ArgumentCaptor.forClass(Subscription.class);

        ArgumentCaptor<String> correlationIdCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(subscriptionEventPublisher, times(3))
                .publishCompensationRequested(
                        subscriptionCaptor.capture(),
                        org.mockito.ArgumentMatchers.eq(assetId),
                        correlationIdCaptor.capture()
                );

        assertThat(subscriptionCaptor.getAllValues())
                .containsExactlyInAnyOrder(
                        processingSubscription,
                        holdSucceededSubscription,
                        confirmedSubscription
                );

        List<String> correlationIds =
                correlationIdCaptor.getAllValues();

        assertThat(correlationIds)
                .hasSize(3)
                .allSatisfy(correlationId ->
                        assertThat(correlationId).isNotBlank()
                );

        assertThat(correlationIds)
                .containsOnly(correlationIds.get(0));

        verify(offeringCompensationCompletionService)
                .completeIfReady(offeringId);
    }

    @Test
    @DisplayName("잠금 대기 중 이미 취소 처리가 시작된 공모는 다시 처리하지 않는다")
    void skipsOfferingChangedAfterCandidateSelection() {
        // given
        UUID offeringId = UUID.randomUUID();
        Instant now = Instant.now();

        Offering offering = mock(Offering.class);

        /*
         * 대상 ID 조회 이후 관리자 중단 등의 작업이 먼저 실행되어
         * 공모가 이미 CANCELLING으로 변경된 상황이다.
         */
        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.CANCELLING);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        // when
        boolean result =
                offeringUnderSubscribedTransactionService
                        .startUnderSubscribedCancellation(
                                offeringId,
                                now
                        );

        // then
        assertThat(result).isFalse();

        verify(offering, never())
                .startUnderSubscribedCancellation();

        verifyNoInteractions(
                subscriptionRepository,
                subscriptionCompensationRepository,
                subscriptionEventPublisher,
                offeringCompensationCompletionService
        );
    }

    @Test
    @DisplayName("보상 대상 청약이 없는 모집 미달 공모도 취소 완료 여부를 확인한다")
    void checksCompletionWithoutCompensatableSubscriptions() {
        // given
        UUID offeringId = UUID.randomUUID();
        Instant now = Instant.now();

        Offering offering = mock(Offering.class);

        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.CLOSED);
        when(offering.getEndAt())
                .thenReturn(now.minusSeconds(1));
        when(offering.getRemainingQuantity())
                .thenReturn(100L);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        when(subscriptionRepository
                .findAllByOfferingIdAndSubscriptionStatusInAndIsDeletedFalse(
                        offeringId,
                        COMPENSATABLE_STATUSES
                ))
                .thenReturn(List.of());

        // when
        boolean result =
                offeringUnderSubscribedTransactionService
                        .startUnderSubscribedCancellation(
                                offeringId,
                                now
                        );

        // then
        assertThat(result).isTrue();

        verify(offering)
                .startUnderSubscribedCancellation();

        verify(offeringCompensationCompletionService)
                .completeIfReady(offeringId);

        verifyNoInteractions(
                subscriptionCompensationRepository,
                subscriptionEventPublisher
        );
    }
}
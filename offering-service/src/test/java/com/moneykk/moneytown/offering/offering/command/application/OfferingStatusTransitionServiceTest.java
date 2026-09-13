package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCancellationResponse;
import com.moneykk.moneytown.offering.offering.command.scheduler.OfferingSchedulerMetrics;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingStatusTransitionServiceTest {

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
    private SubscriptionEventPublisher subscriptionEventPublisher;

    @Mock
    private SubscriptionCompensationRepository subscriptionCompensationRepository;

    @Mock
    private OfferingCompensationCompletionService offeringCompensationCompletionService;

    @Mock
    private OfferingUnderSubscribedTransactionService offeringUnderSubscribedTransactionService;

    @Mock
    private OfferingSchedulerMetrics offeringSchedulerMetrics;

    @InjectMocks
    private OfferingStatusTransitionService offeringStatusTransitionService;

    @Test
    @DisplayName("SCHEDULED 공모의 OPEN 전환 건수를 반환한다")
    void opensScheduledOfferings() {
        // given
        when(offeringRepository.openScheduledOfferings(
                JpaAuditingConfig.SYSTEM_USER_ID
        )).thenReturn(3);

        // when
        int result =
                offeringStatusTransitionService
                        .openScheduledOfferings();

        // then
        assertThat(result).isEqualTo(3);

        verify(offeringRepository)
                .openScheduledOfferings(
                        JpaAuditingConfig.SYSTEM_USER_ID
                );
    }

    @Test
    @DisplayName("SOLD_OUT 공모의 CLOSED 전환 건수를 반환한다")
    void closesSoldOutOfferings() {
        // given
        when(offeringRepository.closeSoldOutOfferings(
                JpaAuditingConfig.SYSTEM_USER_ID
        )).thenReturn(2);

        // when
        int result =
                offeringStatusTransitionService
                        .closeSoldOutOfferings();

        // then
        assertThat(result).isEqualTo(2);

        verify(offeringRepository)
                .closeSoldOutOfferings(
                        JpaAuditingConfig.SYSTEM_USER_ID
                );
    }

    @Test
    @DisplayName("모집 미달 취소를 실제로 시작한 공모 수만 반환한다")
    void returnsOnlySuccessfullyProcessedOfferingCount() {
        // given
        UUID firstOfferingId = UUID.randomUUID();
        UUID secondOfferingId = UUID.randomUUID();

        when(offeringRepository.findUnderSubscribedOfferingIds(
                any(Instant.class),
                any(Pageable.class)
        ))
                .thenReturn(List.of(
                        firstOfferingId,
                        secondOfferingId
                ));

        when(offeringUnderSubscribedTransactionService
                .startUnderSubscribedCancellation(
                        eq(firstOfferingId),
                        any(Instant.class)
                ))
                .thenReturn(true);

        /*
         * ID 조회 후 다른 작업이 먼저 상태를 변경하여
         * 모집 미달 처리 대상이 아니게 된 상황이다.
         */
        when(offeringUnderSubscribedTransactionService
                .startUnderSubscribedCancellation(
                        eq(secondOfferingId),
                        any(Instant.class)
                ))
                .thenReturn(false);

        // when
        int result =
                offeringStatusTransitionService
                        .startUnderSubscribedCancellations();

        // then
        assertThat(result).isEqualTo(1);

        verify(offeringUnderSubscribedTransactionService)
                .startUnderSubscribedCancellation(
                        eq(firstOfferingId),
                        any(Instant.class)
                );

        verify(offeringUnderSubscribedTransactionService)
                .startUnderSubscribedCancellation(
                        eq(secondOfferingId),
                        any(Instant.class)
                );
    }

    @Test
    @DisplayName("한 모집 미달 공모의 처리가 실패해도 다음 공모를 계속 처리한다")
    void continuesAfterIndividualOfferingFailure() {
        // given
        UUID firstOfferingId = UUID.randomUUID();
        UUID failedOfferingId = UUID.randomUUID();
        UUID lastOfferingId = UUID.randomUUID();

        when(offeringRepository.findUnderSubscribedOfferingIds(
                any(Instant.class),
                any(Pageable.class)
        ))
                .thenReturn(List.of(
                        firstOfferingId,
                        failedOfferingId,
                        lastOfferingId
                ));

        when(offeringUnderSubscribedTransactionService
                .startUnderSubscribedCancellation(
                        eq(firstOfferingId),
                        any(Instant.class)
                ))
                .thenReturn(true);

        when(offeringUnderSubscribedTransactionService
                .startUnderSubscribedCancellation(
                        eq(failedOfferingId),
                        any(Instant.class)
                ))
                .thenThrow(
                        new IllegalStateException(
                                "모집 미달 보상 처리 실패"
                        )
                );

        when(offeringUnderSubscribedTransactionService
                .startUnderSubscribedCancellation(
                        eq(lastOfferingId),
                        any(Instant.class)
                ))
                .thenReturn(true);

        // when
        int result =
                offeringStatusTransitionService
                        .startUnderSubscribedCancellations();

        // then
        assertThat(result).isEqualTo(2);

        /*
         * 중간 공모 처리에서 예외가 발생했어도
         * 마지막 공모까지 호출됐는지 검증한다.
         */
        verify(offeringUnderSubscribedTransactionService)
                .startUnderSubscribedCancellation(
                        eq(lastOfferingId),
                        any(Instant.class)
                );

        verify(offeringSchedulerMetrics)
                .recordUnderSubscribedItemFailure();
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

        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.CANCELLED);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        when(subscriptionRepository
                .findAllByOfferingIdAndSubscriptionStatusInAndIsDeletedFalse(
                        offeringId,
                        COMPENSATABLE_STATUSES
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

        verify(offering).startAdminCancellation();

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
                        COMPENSATABLE_STATUSES
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

        verify(offering).startAdminCancellation();

        verify(subscription)
                .startCompensation(
                        CancellationType.OFFERING_ADMIN_CANCELLED
                );

        ArgumentCaptor<SubscriptionCompensation>
                compensationCaptor =
                ArgumentCaptor.forClass(
                        SubscriptionCompensation.class
                );

        verify(subscriptionCompensationRepository)
                .save(compensationCaptor.capture());

        assertThat(
                compensationCaptor.getValue()
                        .getSubscriptionId()
        ).isEqualTo(subscriptionId);

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
        assertThatThrownBy(
                () -> offeringStatusTransitionService
                        .cancelByAdmin(
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
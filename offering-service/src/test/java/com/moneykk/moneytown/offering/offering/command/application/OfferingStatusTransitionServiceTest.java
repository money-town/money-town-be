package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCancellationResponse;
import com.moneykk.moneytown.offering.offering.command.scheduler.OfferingSchedulerMetrics;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.offering.domain.repository.projection.UnderSubscribedOfferingTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingStatusTransitionServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private OfferingCompensationCompletionService
            offeringCompensationCompletionService;

    @Mock
    private OfferingUnderSubscribedTransactionService
            offeringUnderSubscribedTransactionService;

    @Mock
    private OfferingSchedulerMetrics offeringSchedulerMetrics;

    @InjectMocks
    private OfferingStatusTransitionService
            offeringStatusTransitionService;

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

        Instant endAt =
                Instant.parse("2026-09-01T00:00:00Z");

        when(offeringRepository
                .findUnderSubscribedOfferingTargets(
                        any(Instant.class),
                        any(Pageable.class)
                ))
                .thenReturn(List.of(
                        new UnderSubscribedOfferingTarget(
                                firstOfferingId,
                                endAt
                        ),
                        new UnderSubscribedOfferingTarget(
                                secondOfferingId,
                                endAt
                        )
                ));

        when(offeringUnderSubscribedTransactionService
                .startUnderSubscribedCancellation(
                        eq(firstOfferingId),
                        any(Instant.class)
                ))
                .thenReturn(true);

        /*
         * 대상 조회 후 다른 작업이 먼저 상태를 변경하여
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

        Instant endAt =
                Instant.parse("2026-09-01T00:00:00Z");

        when(offeringRepository
                .findUnderSubscribedOfferingTargets(
                        any(Instant.class),
                        any(Pageable.class)
                ))
                .thenReturn(List.of(
                        new UnderSubscribedOfferingTarget(
                                firstOfferingId,
                                endAt
                        ),
                        new UnderSubscribedOfferingTarget(
                                failedOfferingId,
                                endAt
                        ),
                        new UnderSubscribedOfferingTarget(
                                lastOfferingId,
                                endAt
                        )
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
         * 중간 공모 처리에서 예외가 발생해도
         * 마지막 공모까지 처리했는지 검증한다.
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
    @DisplayName(
            "미해결 청약이 없는 공모는 "
                    + "관리자 중단 요청에서 즉시 취소된다"
    )
    void cancelsOfferingImmediatelyWhenNoUnresolvedSubscriptionExists() {
        // given
        UUID offeringId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        Offering offering = mock(Offering.class);

        when(offering.getOfferingId())
                .thenReturn(offeringId);

        /*
         * 완료 서비스가 미해결 청약이 없다고 판단하여
         * 공모를 CANCELLED로 변경한 이후의 상태를 표현한다.
         */
        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.CANCELLED);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

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
    }

    @Test
    @DisplayName(
            "미해결 청약이 있는 공모는 CANCELLING 상태로 "
                    + "보상 배치 처리를 기다린다"
    )
    void leavesOfferingCancellingWhenUnresolvedSubscriptionExists() {
        // given
        UUID offeringId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        Offering offering = mock(Offering.class);

        when(offering.getOfferingId())
                .thenReturn(offeringId);

        /*
         * 완료 서비스가 미해결 청약을 발견해
         * CANCELLING 상태를 유지한 상황을 표현한다.
         */
        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.CANCELLING);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

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

        /*
         * HTTP 요청에서는 개별 청약을 조회하거나
         * 보상 Outbox를 만들지 않는다.
         *
         * 완료 가능 여부만 검사하고, 실제 보상 시작은
         * OfferingCancellationBatchScheduler가 담당한다.
         */
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
                offeringCompensationCompletionService
        );
    }

    @Test
    @DisplayName("첫 배치가 처리되지 않아도 다음 키셋 배치를 계속 처리한다")
    void continuesWithNextKeysetBatchWhenFirstBatchIsNotProcessed() {
        // given
        Instant firstEndAt =
                Instant.parse("2026-09-01T00:00:00Z");

        Instant nextEndAt =
                Instant.parse("2026-09-02T00:00:00Z");

        List<UnderSubscribedOfferingTarget> firstBatch =
                java.util.stream.IntStream.range(0, 100)
                        .mapToObj(index ->
                                new UnderSubscribedOfferingTarget(
                                        UUID.randomUUID(),
                                        firstEndAt
                                )
                        )
                        .sorted(
                                java.util.Comparator.comparing(
                                        UnderSubscribedOfferingTarget
                                                ::offeringId
                                )
                        )
                        .toList();

        UnderSubscribedOfferingTarget lastTarget =
                firstBatch.get(firstBatch.size() - 1);

        UUID nextOfferingId = UUID.randomUUID();

        when(offeringRepository
                .findUnderSubscribedOfferingTargets(
                        any(Instant.class),
                        any(Pageable.class)
                ))
                .thenReturn(firstBatch);

        when(offeringRepository
                .findUnderSubscribedOfferingTargetsAfter(
                        any(Instant.class),
                        eq(lastTarget.endAt()),
                        eq(lastTarget.offeringId()),
                        any(Pageable.class)
                ))
                .thenReturn(List.of(
                        new UnderSubscribedOfferingTarget(
                                nextOfferingId,
                                nextEndAt
                        )
                ));

        when(offeringUnderSubscribedTransactionService
                .startUnderSubscribedCancellation(
                        any(UUID.class),
                        any(Instant.class)
                ))
                .thenReturn(false);

        when(offeringUnderSubscribedTransactionService
                .startUnderSubscribedCancellation(
                        eq(nextOfferingId),
                        any(Instant.class)
                ))
                .thenReturn(true);

        // when
        int result =
                offeringStatusTransitionService
                        .startUnderSubscribedCancellations();

        // then
        assertThat(result).isEqualTo(1);

        verify(offeringRepository)
                .findUnderSubscribedOfferingTargetsAfter(
                        any(Instant.class),
                        eq(lastTarget.endAt()),
                        eq(lastTarget.offeringId()),
                        any(Pageable.class)
                );

        verify(offeringUnderSubscribedTransactionService)
                .startUnderSubscribedCancellation(
                        eq(nextOfferingId),
                        any(Instant.class)
                );
    }
}
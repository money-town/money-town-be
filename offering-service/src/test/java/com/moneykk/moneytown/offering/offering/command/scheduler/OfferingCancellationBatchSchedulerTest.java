package com.moneykk.moneytown.offering.offering.command.scheduler;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCancellationBatchException;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCancellationBatchTransactionService;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCancellationItemTransactionService;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCancellationManualReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingCancellationBatchSchedulerTest {

    @Mock
    private OfferingCancellationBatchTransactionService
            offeringCancellationBatchTransactionService;

    @Mock
    private OfferingCancellationItemTransactionService
            offeringCancellationItemTransactionService;

    @Mock
    private OfferingCancellationManualReviewService
            offeringCancellationManualReviewService;

    @Mock
    private OfferingSchedulerMetrics offeringSchedulerMetrics;

    @InjectMocks
    private OfferingCancellationBatchScheduler scheduler;

    @Test
    @DisplayName("정상 배치가 성공하면 건별 복구를 실행하지 않는다")
    void batchSuccessDoesNotRunIndividualRecovery() {
        // given
        when(
                offeringCancellationBatchTransactionService
                        .compensateNextBatch()
        ).thenReturn(100);

        // when
        scheduler.compensateNextBatch();

        // then
        verify(
                offeringCancellationBatchTransactionService
        ).compensateNextBatch();

        verifyNoInteractions(
                offeringCancellationItemTransactionService,
                offeringCancellationManualReviewService
        );
    }

    @Test
    @DisplayName("배치 대상 선점 실패를 메트릭에 기록하고 예외를 전파하지 않는다")
    void recordsUnrecoverableBatchFailure() {
        when(
                offeringCancellationBatchTransactionService
                        .compensateNextBatch()
        ).thenThrow(new IllegalStateException("배치 대상 선점 실패"));

        assertDoesNotThrow(
                () -> scheduler.compensateNextBatch()
        );

        verify(offeringSchedulerMetrics)
                .recordOfferingCancellationBatchFailure();

        verifyNoInteractions(
                offeringCancellationItemTransactionService,
                offeringCancellationManualReviewService
        );
    }

    @Test
    @DisplayName("배치 실패 시 포함된 청약을 건별로 재처리한다")
    void batchFailureRunsIndividualRecovery() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID firstSubscriptionId = UUID.randomUUID();
        UUID secondSubscriptionId = UUID.randomUUID();

        OfferingCancellationBatchException batchException =
                new OfferingCancellationBatchException(
                        offeringId,
                        List.of(
                                firstSubscriptionId,
                                secondSubscriptionId
                        ),
                        new IllegalStateException(
                                "배치 처리 실패"
                        )
                );

        when(
                offeringCancellationBatchTransactionService
                        .compensateNextBatch()
        ).thenThrow(batchException);

        when(
                offeringCancellationItemTransactionService
                        .compensate(
                                offeringId,
                                firstSubscriptionId
                        )
        ).thenReturn(true);

        when(
                offeringCancellationItemTransactionService
                        .compensate(
                                offeringId,
                                secondSubscriptionId
                        )
        ).thenReturn(true);

        // when
        scheduler.compensateNextBatch();

        // then
        verify(
                offeringCancellationItemTransactionService
        ).compensate(
                offeringId,
                firstSubscriptionId
        );

        verify(
                offeringCancellationItemTransactionService
        ).compensate(
                offeringId,
                secondSubscriptionId
        );

        verifyNoInteractions(
                offeringCancellationManualReviewService
        );
    }

    @Test
    @DisplayName("특정 청약의 업무 오류는 MANUAL_REVIEW로 격리하고 다음 청약을 처리한다")
    void businessFailureMovesItemToManualReviewAndContinues() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID failedSubscriptionId = UUID.randomUUID();
        UUID nextSubscriptionId = UUID.randomUUID();

        OfferingCancellationBatchException batchException =
                new OfferingCancellationBatchException(
                        offeringId,
                        List.of(
                                failedSubscriptionId,
                                nextSubscriptionId
                        ),
                        new IllegalStateException(
                                "배치 처리 실패"
                        )
                );

        when(
                offeringCancellationBatchTransactionService
                        .compensateNextBatch()
        ).thenThrow(batchException);

        when(
                offeringCancellationItemTransactionService
                        .compensate(
                                offeringId,
                                failedSubscriptionId
                        )
        ).thenThrow(
                new BusinessException(
                        OfferingErrorCode.INVALID_OFFERING_INPUT
                )
        );

        when(
                offeringCancellationManualReviewService
                        .markForManualReview(
                                offeringId,
                                failedSubscriptionId
                        )
        ).thenReturn(true);

        when(
                offeringCancellationItemTransactionService
                        .compensate(
                                offeringId,
                                nextSubscriptionId
                        )
        ).thenReturn(true);

        // when
        scheduler.compensateNextBatch();

        // then
        verify(
                offeringCancellationManualReviewService
        ).markForManualReview(
                offeringId,
                failedSubscriptionId
        );

        verify(
                offeringCancellationItemTransactionService
        ).compensate(
                offeringId,
                nextSubscriptionId
        );
    }

    @Test
    @DisplayName("DB 잠금 실패가 발생하면 MANUAL_REVIEW로 전환하지 않고 건별 복구를 중단한다")
    void transientDatabaseFailureStopsRecoveryWithoutManualReview() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID failedSubscriptionId = UUID.randomUUID();
        UUID nextSubscriptionId = UUID.randomUUID();

        OfferingCancellationBatchException batchException =
                new OfferingCancellationBatchException(
                        offeringId,
                        List.of(
                                failedSubscriptionId,
                                nextSubscriptionId
                        ),
                        new CannotAcquireLockException(
                                "DB 잠금 실패"
                        )
                );

        when(
                offeringCancellationBatchTransactionService
                        .compensateNextBatch()
        ).thenThrow(batchException);

        when(
                offeringCancellationItemTransactionService
                        .compensate(
                                offeringId,
                                failedSubscriptionId
                        )
        ).thenThrow(
                new CannotAcquireLockException(
                        "DB 잠금 실패"
                )
        );

        // when
        scheduler.compensateNextBatch();

        // then
        verify(
                offeringCancellationManualReviewService,
                never()
        ).markForManualReview(
                offeringId,
                failedSubscriptionId
        );

        verify(
                offeringCancellationItemTransactionService,
                never()
        ).compensate(
                offeringId,
                nextSubscriptionId
        );
    }

    @Test
    @DisplayName("알 수 없는 오류는 대량 MANUAL_REVIEW를 막기 위해 격리하지 않고 중단한다")
    void unknownFailureStopsRecoveryWithoutManualReview() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID failedSubscriptionId = UUID.randomUUID();
        UUID nextSubscriptionId = UUID.randomUUID();

        OfferingCancellationBatchException batchException =
                new OfferingCancellationBatchException(
                        offeringId,
                        List.of(
                                failedSubscriptionId,
                                nextSubscriptionId
                        ),
                        new IllegalStateException(
                                "Outbox 직렬화 실패"
                        )
                );

        when(
                offeringCancellationBatchTransactionService
                        .compensateNextBatch()
        ).thenThrow(batchException);

        when(
                offeringCancellationItemTransactionService
                        .compensate(
                                offeringId,
                                failedSubscriptionId
                        )
        ).thenThrow(
                new IllegalStateException(
                        "Outbox 직렬화 실패"
                )
        );

        // when
        scheduler.compensateNextBatch();

        // then
        verifyNoInteractions(
                offeringCancellationManualReviewService
        );

        verify(
                offeringCancellationItemTransactionService,
                never()
        ).compensate(
                offeringId,
                nextSubscriptionId
        );
    }
}

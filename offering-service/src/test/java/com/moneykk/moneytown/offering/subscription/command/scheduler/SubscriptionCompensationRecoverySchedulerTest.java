package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionCompensationRecoveryBatchException;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionCompensationRecoveryTransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionCompensationRecoverySchedulerTest {

    @Mock
    private SubscriptionCompensationRecoveryTransactionService
            recoveryTransactionService;

    @InjectMocks
    private SubscriptionCompensationRecoveryScheduler scheduler;

    @Test
    @DisplayName("장기 미완료 보상 복구를 공모 선점 배치에 위임한다")
    void delegatesRecoveryToClaimedBatch() {
        when(recoveryTransactionService
                .markNextStuckBatchForManualReview(
                        any(Instant.class),
                        eq(100)
                ))
                .thenReturn(2);

        scheduler.recoverStuckCompensations();

        verify(recoveryTransactionService)
                .markNextStuckBatchForManualReview(
                        any(Instant.class),
                        eq(100)
                );

        verifyNoMoreInteractions(recoveryTransactionService);
    }

    @Test
    @DisplayName("처리 대상이 없으면 건별 복구를 실행하지 않는다")
    void skipsIndividualRecoveryWhenNoCandidateExists() {
        when(recoveryTransactionService
                .markNextStuckBatchForManualReview(
                        any(Instant.class),
                        eq(100)
                ))
                .thenReturn(0);

        scheduler.recoverStuckCompensations();

        verify(recoveryTransactionService, never())
                .markStuckForManualReview(
                        any(UUID.class),
                        any(Instant.class)
                );
    }

    @Test
    @DisplayName("배치 실패 시 포함된 청약을 건별 독립 트랜잭션으로 재처리한다")
    void retriesFailedBatchIndividually() {
        UUID offeringId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        when(recoveryTransactionService
                .markNextStuckBatchForManualReview(
                        any(Instant.class),
                        eq(100)
                ))
                .thenThrow(
                        new SubscriptionCompensationRecoveryBatchException(
                                offeringId,
                                List.of(firstId, secondId),
                                new IllegalStateException("배치 실패")
                        )
                );

        when(recoveryTransactionService
                .markStuckForManualReview(
                        eq(firstId),
                        any(Instant.class)
                ))
                .thenReturn(true);

        when(recoveryTransactionService
                .markStuckForManualReview(
                        eq(secondId),
                        any(Instant.class)
                ))
                .thenReturn(false);

        scheduler.recoverStuckCompensations();

        verify(recoveryTransactionService)
                .markStuckForManualReview(
                        eq(firstId),
                        any(Instant.class)
                );

        verify(recoveryTransactionService)
                .markStuckForManualReview(
                        eq(secondId),
                        any(Instant.class)
                );
    }

    @Test
    @DisplayName("건별 복구 중 시스템 장애가 발생하면 남은 청약 처리를 중단한다")
    void stopsIndividualRecoveryAfterSystemFailure() {
        UUID offeringId = UUID.randomUUID();
        UUID failedId = UUID.randomUUID();
        UUID nextId = UUID.randomUUID();

        when(recoveryTransactionService
                .markNextStuckBatchForManualReview(
                        any(Instant.class),
                        eq(100)
                ))
                .thenThrow(
                        new SubscriptionCompensationRecoveryBatchException(
                                offeringId,
                                List.of(failedId, nextId),
                                new IllegalStateException("배치 실패")
                        )
                );

        when(recoveryTransactionService
                .markStuckForManualReview(
                        eq(failedId),
                        any(Instant.class)
                ))
                .thenThrow(
                        new CannotAcquireLockException("잠금 실패")
                );

        scheduler.recoverStuckCompensations();

        verify(recoveryTransactionService, never())
                .markStuckForManualReview(
                        eq(nextId),
                        any(Instant.class)
                );
    }

    @Test
    @DisplayName("특정 청약 오류는 기록하고 같은 배치의 다음 청약을 처리한다")
    void continuesAfterNonSystemItemFailure() {
        UUID offeringId = UUID.randomUUID();
        UUID failedId = UUID.randomUUID();
        UUID nextId = UUID.randomUUID();

        when(recoveryTransactionService
                .markNextStuckBatchForManualReview(
                        any(Instant.class),
                        eq(100)
                ))
                .thenThrow(
                        new SubscriptionCompensationRecoveryBatchException(
                                offeringId,
                                List.of(failedId, nextId),
                                new IllegalStateException("배치 실패")
                        )
                );

        when(recoveryTransactionService
                .markStuckForManualReview(
                        eq(failedId),
                        any(Instant.class)
                ))
                .thenThrow(new IllegalStateException("데이터 오류"));

        when(recoveryTransactionService
                .markStuckForManualReview(
                        eq(nextId),
                        any(Instant.class)
                ))
                .thenReturn(true);

        scheduler.recoverStuckCompensations();

        verify(recoveryTransactionService)
                .markStuckForManualReview(
                        eq(nextId),
                        any(Instant.class)
                );
    }
}

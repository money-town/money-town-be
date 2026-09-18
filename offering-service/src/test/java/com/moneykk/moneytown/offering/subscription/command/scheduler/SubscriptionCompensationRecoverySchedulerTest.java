package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionCompensationRecoveryTransactionService;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionCompensationRecoverySchedulerTest {

    @Mock
    private SubscriptionCompensationRepository
            subscriptionCompensationRepository;

    @Mock
    private SubscriptionCompensationRecoveryTransactionService
            recoveryTransactionService;

    @InjectMocks
    private SubscriptionCompensationRecoveryScheduler scheduler;

    @Test
    @DisplayName("장기 미완료 보상을 조회해 건별 복구 서비스에 위임한다")
    void delegatesStuckCompensationsIndividually() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        when(subscriptionCompensationRepository
                .findStuckCompensationSubscriptionIds(
                        any(Instant.class),
                        eq(100)
                ))
                .thenReturn(List.of(firstId, secondId));

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
    @DisplayName("처리 대상이 없으면 건별 복구 서비스를 호출하지 않는다")
    void skipsWhenNoCandidateExists() {
        when(subscriptionCompensationRepository
                .findStuckCompensationSubscriptionIds(
                        any(Instant.class),
                        eq(100)
                ))
                .thenReturn(List.of());

        scheduler.recoverStuckCompensations();

        verify(
                recoveryTransactionService,
                never()
        ).markStuckForManualReview(
                any(UUID.class),
                any(Instant.class)
        );
    }

    @Test
    @DisplayName("특정 청약 처리 실패가 다른 청약의 격리를 막지 않는다")
    void continuesAfterItemFailure() {
        UUID failedId = UUID.randomUUID();
        UUID nextId = UUID.randomUUID();

        when(subscriptionCompensationRepository
                .findStuckCompensationSubscriptionIds(
                        any(Instant.class),
                        eq(100)
                ))
                .thenReturn(List.of(failedId, nextId));

        when(recoveryTransactionService
                .markStuckForManualReview(
                        eq(failedId),
                        any(Instant.class)
                ))
                .thenThrow(new IllegalStateException("테스트 실패"));

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
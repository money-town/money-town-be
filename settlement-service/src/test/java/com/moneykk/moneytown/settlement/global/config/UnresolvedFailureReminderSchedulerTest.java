package com.moneykk.moneytown.settlement.global.config;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.infrastructure.client.SettlementFailureNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnresolvedFailureReminderSchedulerTest {

    private static final List<SettlementStatus> UNRESOLVED = List.of(SettlementStatus.FAILED, SettlementStatus.PARTIAL_FAILED);

    @Mock
    private SettlementBatchRepository settlementBatchRepository;
    @Mock
    private FinalSettlementBatchRepository finalSettlementBatchRepository;
    @Mock
    private SettlementFailureNotifier settlementFailureNotifier;

    @InjectMocks
    private UnresolvedFailureReminderScheduler scheduler;

    @Test
    @DisplayName("FAILED/PARTIAL_FAILED 배당·최종 정산 회차를 각각 재통보 대상으로 넘긴다")
    void remindsEveryUnresolvedBatch() {
        SettlementBatch dividend = SettlementBatch.open(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1), 1_000L);
        FinalSettlementBatch finalBatch = FinalSettlementBatch.open(UUID.randomUUID(), Instant.now(), 1_000L, 9_000L);
        when(settlementBatchRepository.findByStatusInAndIsDeletedFalse(UNRESOLVED)).thenReturn(List.of(dividend));
        when(finalSettlementBatchRepository.findByStatusInAndIsDeletedFalse(UNRESOLVED)).thenReturn(List.of(finalBatch));

        scheduler.remindUnresolvedFailures();

        verify(settlementFailureNotifier).remindUnresolvedDividendBatch(dividend, null);
        verify(settlementFailureNotifier).remindUnresolvedFinalSettlementBatch(finalBatch);
    }

    @Test
    @DisplayName("스캔 대상은 실패 상태 둘뿐이라 COMPLETED/CLOSED_ABANDONED로 마감된 회차는 조회되지 않는다 (마감되면 재통보 중단)")
    void queriesOnlyUnresolvedStatuses() {
        when(settlementBatchRepository.findByStatusInAndIsDeletedFalse(UNRESOLVED)).thenReturn(List.of());
        when(finalSettlementBatchRepository.findByStatusInAndIsDeletedFalse(UNRESOLVED)).thenReturn(List.of());

        scheduler.remindUnresolvedFailures();

        verifyNoInteractions(settlementFailureNotifier);
    }

    @Test
    @DisplayName("한 회차의 재통보가 예외로 실패해도 나머지 회차는 계속 처리한다")
    void isolatesFailurePerBatch() {
        SettlementBatch failing = SettlementBatch.open(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1), 1_000L);
        SettlementBatch healthy = SettlementBatch.open(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 2), 1_000L);
        FinalSettlementBatch finalBatch = FinalSettlementBatch.open(UUID.randomUUID(), Instant.now(), 1_000L, 9_000L);
        when(settlementBatchRepository.findByStatusInAndIsDeletedFalse(UNRESOLVED)).thenReturn(List.of(failing, healthy));
        when(finalSettlementBatchRepository.findByStatusInAndIsDeletedFalse(UNRESOLVED)).thenReturn(List.of(finalBatch));
        doThrow(new RuntimeException("boom")).when(settlementFailureNotifier).remindUnresolvedDividendBatch(failing, null);

        scheduler.remindUnresolvedFailures();

        verify(settlementFailureNotifier).remindUnresolvedDividendBatch(healthy, null);
        verify(settlementFailureNotifier).remindUnresolvedFinalSettlementBatch(finalBatch);
    }

    @Test
    @DisplayName("배당 회차 조회가 실패해도 최종 정산 회차 재통보는 계속 진행한다")
    void dividendQueryFailureDoesNotBlockFinalSettlementReminders() {
        FinalSettlementBatch finalBatch = FinalSettlementBatch.open(UUID.randomUUID(), Instant.now(), 1_000L, 9_000L);
        when(settlementBatchRepository.findByStatusInAndIsDeletedFalse(UNRESOLVED)).thenThrow(new RuntimeException("db down"));
        when(finalSettlementBatchRepository.findByStatusInAndIsDeletedFalse(UNRESOLVED)).thenReturn(List.of(finalBatch));

        scheduler.remindUnresolvedFailures();

        verify(settlementFailureNotifier).remindUnresolvedFinalSettlementBatch(finalBatch);
        verify(settlementFailureNotifier, never()).remindUnresolvedDividendBatch(any(), any());
    }

    @Test
    @DisplayName("최종 정산 회차 조회가 실패해도 배당 회차 재통보는 계속 진행한다")
    void finalSettlementQueryFailureDoesNotBlockDividendReminders() {
        SettlementBatch dividend = SettlementBatch.open(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1), 1_000L);
        when(settlementBatchRepository.findByStatusInAndIsDeletedFalse(UNRESOLVED)).thenReturn(List.of(dividend));
        when(finalSettlementBatchRepository.findByStatusInAndIsDeletedFalse(UNRESOLVED)).thenThrow(new RuntimeException("db down"));

        scheduler.remindUnresolvedFailures();

        verify(settlementFailureNotifier).remindUnresolvedDividendBatch(dividend, null);
        verify(settlementFailureNotifier, never()).remindUnresolvedFinalSettlementBatch(any());
    }
}

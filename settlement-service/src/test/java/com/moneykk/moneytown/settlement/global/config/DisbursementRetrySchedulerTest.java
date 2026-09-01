package com.moneykk.moneytown.settlement.global.config;

import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementDisbursementService;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementPayoutRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisbursementRetrySchedulerTest {

    private static final List<PayoutStatus> RESUMABLE_STATUSES = List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING);

    @Mock
    private DividendPayoutRepository dividendPayoutRepository;
    @Mock
    private DividendDisbursementService dividendDisbursementService;
    @Mock
    private FinalSettlementPayoutRepository finalSettlementPayoutRepository;
    @Mock
    private FinalSettlementDisbursementService finalSettlementDisbursementService;

    @InjectMocks
    private DisbursementRetryScheduler disbursementRetryScheduler;

    @Test
    @DisplayName("QUEUED/RETRYING 배당 지급이 남은 회차마다 disburseAsync를 재트리거한다")
    void retriggersDividendDisbursementForStuckBatches() {
        UUID batchId1 = UUID.randomUUID();
        UUID batchId2 = UUID.randomUUID();
        when(dividendPayoutRepository.findDistinctSettlementBatchIdByStatusIn(RESUMABLE_STATUSES))
                .thenReturn(List.of(batchId1, batchId2));

        disbursementRetryScheduler.retryStuckDividendPayouts();

        verify(dividendDisbursementService).reclaimStalledProcessing(any());
        verify(dividendDisbursementService).disburseAsync(batchId1);
        verify(dividendDisbursementService).disburseAsync(batchId2);
    }

    @Test
    @DisplayName("재트리거할 배당 회차가 없으면 disburseAsync를 호출하지 않는다")
    void doesNothingWhenNoDividendBatchesStuck() {
        when(dividendPayoutRepository.findDistinctSettlementBatchIdByStatusIn(RESUMABLE_STATUSES))
                .thenReturn(List.of());

        disbursementRetryScheduler.retryStuckDividendPayouts();

        verify(dividendDisbursementService, never()).disburseAsync(any());
    }

    @Test
    @DisplayName("QUEUED/RETRYING 최종 정산 지급이 남은 회차마다 disburseAsync를 재트리거한다")
    void retriggersFinalSettlementDisbursementForStuckBatches() {
        UUID batchId1 = UUID.randomUUID();
        when(finalSettlementPayoutRepository.findDistinctFinalSettlementBatchIdByStatusIn(RESUMABLE_STATUSES))
                .thenReturn(List.of(batchId1));

        disbursementRetryScheduler.retryStuckFinalSettlementPayouts();

        verify(finalSettlementDisbursementService).reclaimStalledProcessing(any());
        verify(finalSettlementDisbursementService).disburseAsync(batchId1);
    }

    @Test
    @DisplayName("재트리거할 최종 정산 회차가 없으면 disburseAsync를 호출하지 않는다")
    void doesNothingWhenNoFinalSettlementBatchesStuck() {
        when(finalSettlementPayoutRepository.findDistinctFinalSettlementBatchIdByStatusIn(RESUMABLE_STATUSES))
                .thenReturn(List.of());

        disbursementRetryScheduler.retryStuckFinalSettlementPayouts();

        verify(finalSettlementDisbursementService, never()).disburseAsync(any());
    }
}
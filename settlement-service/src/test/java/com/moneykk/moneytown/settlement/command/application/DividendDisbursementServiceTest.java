package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.DividendDepositRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.DividendDepositResponse;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DividendDisbursementServiceTest {

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final LocalDate RECORD_DATE = LocalDate.of(2026, 9, 1);

    @Mock
    private SettlementBatchRepository settlementBatchRepository;
    @Mock
    private DividendPayoutRepository dividendPayoutRepository;
    @Mock
    private WalletServiceClient walletServiceClient;

    @InjectMocks
    private DividendDisbursementService dividendDisbursementService;

    @Test
    @DisplayName("지급 성공 건은 PAID로 전환하고, 전부 성공하면 배치를 COMPLETED로 전환한다")
    void paysOutSuccessfullyAndCompletesBatch() {
        SettlementBatch batch = disbursingBatch();
        DividendPayout payout = queuedPayout(batch.getId());
        stubBatch(batch);
        stubPending(batch.getId(), List.of(payout));
        stubAllPayouts(batch.getId(), List.of(payout));

        DividendDepositResponse response = new DividendDepositResponse(9012L, 55L, "DIVIDEND", payout.getAmount(), Instant.now(), null);
        when(walletServiceClient.depositDividend(any())).thenReturn(ApiResponse.success(response, null));

        dividendDisbursementService.disburse(batch.getId());

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.COMPLETED);

        ArgumentCaptor<DividendDepositRequest> requestCaptor = ArgumentCaptor.forClass(DividendDepositRequest.class);
        verify(walletServiceClient).depositDividend(requestCaptor.capture());
        assertThat(requestCaptor.getValue().idempotencyKey()).isEqualTo(payout.getId().toString());
        assertThat(requestCaptor.getValue().investorId()).isEqualTo(payout.getInvestorId());
        assertThat(requestCaptor.getValue().settlementBatchId()).isEqualTo(batch.getId());
        assertThat(requestCaptor.getValue().amount()).isEqualTo(payout.getAmount());

        verify(dividendPayoutRepository).saveAll(List.of(payout));
        verify(settlementBatchRepository).save(batch);
    }

    @Test
    @DisplayName("지갑 호출 실패 시 retryCount를 올리고 RETRYING으로 남기며, 배치는 DISBURSING을 유지한다")
    void incrementsRetryCountAndKeepsRetryingOnFailure() {
        SettlementBatch batch = disbursingBatch();
        DividendPayout payout = queuedPayout(batch.getId());
        stubBatch(batch);
        stubPending(batch.getId(), List.of(payout));
        stubAllPayouts(batch.getId(), List.of(payout));

        when(walletServiceClient.depositDividend(any())).thenThrow(mock(FeignException.class));

        dividendDisbursementService.disburse(batch.getId());

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.RETRYING);
        assertThat(payout.getRetryCount()).isEqualTo(1);
        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.DISBURSING);
    }

    @Test
    @DisplayName("RETRYING 건도 재처리 대상에 포함되며, 3회째 실패하면 DEAD_LETTER로 전환한다 — 일부만 성공하면 PARTIAL_FAILED")
    void movesToDeadLetterAfterMaxRetriesAndMarksPartialFailed() {
        SettlementBatch batch = disbursingBatch();
        DividendPayout exhausted = retryingPayout(batch.getId(), 2);
        DividendPayout succeeding = queuedPayout(batch.getId());
        stubBatch(batch);
        stubPending(batch.getId(), List.of(exhausted, succeeding));
        stubAllPayouts(batch.getId(), List.of(exhausted, succeeding));

        DividendDepositResponse response = new DividendDepositResponse(9012L, 55L, "DIVIDEND", succeeding.getAmount(), Instant.now(), null);
        when(walletServiceClient.depositDividend(any()))
                .thenThrow(mock(FeignException.class))
                .thenReturn(ApiResponse.success(response, null));

        dividendDisbursementService.disburse(batch.getId());

        assertThat(exhausted.getStatus()).isEqualTo(PayoutStatus.DEAD_LETTER);
        assertThat(exhausted.getRetryCount()).isEqualTo(3);
        assertThat(succeeding.getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.PARTIAL_FAILED);
    }

    @Test
    @DisplayName("모든 건이 DEAD_LETTER면 배치를 FAILED로 전환한다")
    void marksFailedWhenAllDeadLetter() {
        SettlementBatch batch = disbursingBatch();
        DividendPayout exhausted = retryingPayout(batch.getId(), 2);
        stubBatch(batch);
        stubPending(batch.getId(), List.of(exhausted));
        stubAllPayouts(batch.getId(), List.of(exhausted));

        when(walletServiceClient.depositDividend(any())).thenThrow(mock(FeignException.class));

        dividendDisbursementService.disburse(batch.getId());

        assertThat(exhausted.getStatus()).isEqualTo(PayoutStatus.DEAD_LETTER);
        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.FAILED);
    }

    @Test
    @DisplayName("존재하지 않는 정산 회차면 예외")
    void rejectsWhenBatchNotFound() {
        UUID unknownBatchId = UUID.randomUUID();
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(unknownBatchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dividendDisbursementService.disburse(unknownBatchId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND);
    }

    private void stubBatch(SettlementBatch batch) {
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
    }

    private void stubPending(UUID batchId, List<DividendPayout> payouts) {
        when(dividendPayoutRepository.findBySettlementBatchIdAndStatusInAndIsDeletedFalse(
                batchId, List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING)))
                .thenReturn(payouts);
    }

    private void stubAllPayouts(UUID batchId, List<DividendPayout> payouts) {
        when(dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batchId)).thenReturn(payouts);
    }

    private SettlementBatch disbursingBatch() {
        SettlementBatch batch = SettlementBatch.open(ASSET_ID, UUID.randomUUID(), RECORD_DATE, 1_000_000L, 0L);
        ReflectionTestUtils.setField(batch, "status", SettlementStatus.DISBURSING);
        return batch;
    }

    private DividendPayout queuedPayout(UUID batchId) {
        return DividendPayout.queue(batchId, UUID.randomUUID(), BigDecimal.ONE, 1_000_000L);
    }

    private DividendPayout retryingPayout(UUID batchId, int retryCount) {
        DividendPayout payout = DividendPayout.queue(batchId, UUID.randomUUID(), BigDecimal.ONE, 1_000_000L);
        ReflectionTestUtils.setField(payout, "status", PayoutStatus.RETRYING);
        ReflectionTestUtils.setField(payout, "retryCount", retryCount);
        return payout;
    }
}
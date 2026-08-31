package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementPayoutRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.SettlementDepositRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.SettlementDepositResponse;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
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
class FinalSettlementDisbursementServiceTest {

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final Instant TERMINATED_AT = Instant.parse("2026-08-31T00:00:00Z");
    private static final Long UNIT_PRICE = 1_000_000L;

    @Mock
    private FinalSettlementBatchRepository finalSettlementBatchRepository;
    @Mock
    private FinalSettlementPayoutRepository finalSettlementPayoutRepository;
    @Mock
    private WalletServiceClient walletServiceClient;

    @InjectMocks
    private FinalSettlementDisbursementService finalSettlementDisbursementService;

    @Test
    @DisplayName("지급 성공 건은 PAID로 전환하고, 전부 성공하면 배치를 COMPLETED로 전환한다")
    void paysOutSuccessfullyAndCompletesBatch() {
        FinalSettlementBatch batch = disbursingBatch();
        FinalSettlementPayout payout = queuedPayout(batch.getId());
        stubBatch(batch);
        stubPending(batch.getId(), List.of(payout));
        stubAllPayouts(batch.getId(), List.of(payout));

        SettlementDepositResponse response = new SettlementDepositResponse(9013L, 55L, "SETTLEMENT", payout.getAmount(), Instant.now(), null);
        when(walletServiceClient.depositSettlement(any())).thenReturn(ApiResponse.success(response, null));

        finalSettlementDisbursementService.disburse(batch.getId());

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.COMPLETED);

        ArgumentCaptor<SettlementDepositRequest> requestCaptor = ArgumentCaptor.forClass(SettlementDepositRequest.class);
        verify(walletServiceClient).depositSettlement(requestCaptor.capture());
        assertThat(requestCaptor.getValue().idempotencyKey()).isEqualTo(payout.getId().toString());
        assertThat(requestCaptor.getValue().investorId()).isEqualTo(payout.getInvestorId());
        assertThat(requestCaptor.getValue().finalSettlementBatchId()).isEqualTo(batch.getId());
        assertThat(requestCaptor.getValue().amount()).isEqualTo(payout.getAmount());

        verify(finalSettlementPayoutRepository).saveAll(List.of(payout));
        verify(finalSettlementBatchRepository).save(batch);
    }

    @Test
    @DisplayName("지갑 호출 실패 시 retryCount를 올리고 RETRYING으로 남기며, 배치는 DISBURSING을 유지한다")
    void incrementsRetryCountAndKeepsRetryingOnFailure() {
        FinalSettlementBatch batch = disbursingBatch();
        FinalSettlementPayout payout = queuedPayout(batch.getId());
        stubBatch(batch);
        stubPending(batch.getId(), List.of(payout));
        stubAllPayouts(batch.getId(), List.of(payout));

        when(walletServiceClient.depositSettlement(any())).thenThrow(mock(FeignException.class));

        finalSettlementDisbursementService.disburse(batch.getId());

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.RETRYING);
        assertThat(payout.getRetryCount()).isEqualTo(1);
        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.DISBURSING);
    }

    @Test
    @DisplayName("RETRYING 건도 재처리 대상에 포함되며, 3회째 실패하면 DEAD_LETTER로 전환한다 — 일부만 성공하면 PARTIAL_FAILED")
    void movesToDeadLetterAfterMaxRetriesAndMarksPartialFailed() {
        FinalSettlementBatch batch = disbursingBatch();
        FinalSettlementPayout exhausted = retryingPayout(batch.getId(), 2);
        FinalSettlementPayout succeeding = queuedPayout(batch.getId());
        stubBatch(batch);
        stubPending(batch.getId(), List.of(exhausted, succeeding));
        stubAllPayouts(batch.getId(), List.of(exhausted, succeeding));

        SettlementDepositResponse response = new SettlementDepositResponse(9013L, 55L, "SETTLEMENT", succeeding.getAmount(), Instant.now(), null);
        when(walletServiceClient.depositSettlement(any()))
                .thenThrow(mock(FeignException.class))
                .thenReturn(ApiResponse.success(response, null));

        finalSettlementDisbursementService.disburse(batch.getId());

        assertThat(exhausted.getStatus()).isEqualTo(PayoutStatus.DEAD_LETTER);
        assertThat(exhausted.getRetryCount()).isEqualTo(3);
        assertThat(succeeding.getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.PARTIAL_FAILED);
    }

    @Test
    @DisplayName("모든 건이 DEAD_LETTER면 배치를 FAILED로 전환한다")
    void marksFailedWhenAllDeadLetter() {
        FinalSettlementBatch batch = disbursingBatch();
        FinalSettlementPayout exhausted = retryingPayout(batch.getId(), 2);
        stubBatch(batch);
        stubPending(batch.getId(), List.of(exhausted));
        stubAllPayouts(batch.getId(), List.of(exhausted));

        when(walletServiceClient.depositSettlement(any())).thenThrow(mock(FeignException.class));

        finalSettlementDisbursementService.disburse(batch.getId());

        assertThat(exhausted.getStatus()).isEqualTo(PayoutStatus.DEAD_LETTER);
        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.FAILED);
    }

    @Test
    @DisplayName("존재하지 않는 최종 정산 회차면 예외")
    void rejectsWhenBatchNotFound() {
        UUID unknownBatchId = UUID.randomUUID();
        when(finalSettlementBatchRepository.findByIdAndIsDeletedFalse(unknownBatchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finalSettlementDisbursementService.disburse(unknownBatchId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.FINAL_SETTLEMENT_BATCH_NOT_FOUND);
    }

    private void stubBatch(FinalSettlementBatch batch) {
        when(finalSettlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
    }

    private void stubPending(UUID batchId, List<FinalSettlementPayout> payouts) {
        when(finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndStatusInAndIsDeletedFalse(
                batchId, List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING)))
                .thenReturn(payouts);
    }

    private void stubAllPayouts(UUID batchId, List<FinalSettlementPayout> payouts) {
        when(finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndIsDeletedFalse(batchId)).thenReturn(payouts);
    }

    private FinalSettlementBatch disbursingBatch() {
        FinalSettlementBatch batch = FinalSettlementBatch.open(ASSET_ID, TERMINATED_AT, UNIT_PRICE, 900_000_000L);
        batch.markCalculated();
        batch.markDisbursing();
        return batch;
    }

    private FinalSettlementPayout queuedPayout(UUID batchId) {
        return FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 900L, 1_000_000L);
    }

    private FinalSettlementPayout retryingPayout(UUID batchId, int retryCount) {
        FinalSettlementPayout payout = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 900L, 1_000_000L);
        ReflectionTestUtils.setField(payout, "status", PayoutStatus.RETRYING);
        ReflectionTestUtils.setField(payout, "retryCount", retryCount);
        return payout;
    }
}
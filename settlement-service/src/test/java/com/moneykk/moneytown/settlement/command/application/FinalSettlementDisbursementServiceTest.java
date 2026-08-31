package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.SettlementDepositRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.SettlementDepositResponse;
import feign.FeignException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalSettlementDisbursementServiceTest {

    @Mock
    private FinalSettlementPayoutWriter payoutWriter;
    @Mock
    private WalletServiceClient walletServiceClient;

    @InjectMocks
    private FinalSettlementDisbursementService finalSettlementDisbursementService;

    @Test
    @DisplayName("markDisbursing → 지갑 호출 → markPaid → updateBatchStatus 순서로 처리하고, 각 단계는 건별로 커밋된다")
    void disbursesInOrderPerPayout() {
        UUID batchId = UUID.randomUUID();
        FinalSettlementPayout payout = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 900L, 1_000_000L);
        when(payoutWriter.findPendingPayouts(batchId)).thenReturn(List.of(payout));
        SettlementDepositResponse response = new SettlementDepositResponse(9013L, 55L, "SETTLEMENT", payout.getAmount(), Instant.now(), null);
        when(walletServiceClient.depositSettlement(any())).thenReturn(ApiResponse.success(response, null));

        finalSettlementDisbursementService.disburse(batchId);

        InOrder order = inOrder(payoutWriter, walletServiceClient);
        order.verify(payoutWriter).markDisbursing(batchId);
        order.verify(walletServiceClient).depositSettlement(any());
        order.verify(payoutWriter).markPaid(payout.getId());
        order.verify(payoutWriter).updateBatchStatus(batchId);
        verify(payoutWriter, never()).markFailedAttempt(any());

        ArgumentCaptor<SettlementDepositRequest> requestCaptor = ArgumentCaptor.forClass(SettlementDepositRequest.class);
        verify(walletServiceClient).depositSettlement(requestCaptor.capture());
        assertThat(requestCaptor.getValue().idempotencyKey()).isEqualTo(payout.getId().toString());
        assertThat(requestCaptor.getValue().investorId()).isEqualTo(payout.getInvestorId());
        assertThat(requestCaptor.getValue().finalSettlementBatchId()).isEqualTo(batchId);
        assertThat(requestCaptor.getValue().amount()).isEqualTo(payout.getAmount());
    }

    @Test
    @DisplayName("지갑 응답이 success=false면 예외가 없어도 markPaid 대신 markFailedAttempt를 호출한다")
    void marksFailedAttemptWhenResponseSuccessIsFalse() {
        UUID batchId = UUID.randomUUID();
        FinalSettlementPayout payout = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 900L, 1_000_000L);
        when(payoutWriter.findPendingPayouts(batchId)).thenReturn(List.of(payout));
        when(walletServiceClient.depositSettlement(any()))
                .thenReturn(new ApiResponse<>(false, null, "지갑 처리 실패", "WALLET_500_01"));

        finalSettlementDisbursementService.disburse(batchId);

        verify(payoutWriter).markFailedAttempt(payout.getId());
        verify(payoutWriter, never()).markPaid(any());
    }

    @Test
    @DisplayName("지갑 호출이 FeignException을 던지면 markPaid 대신 markFailedAttempt를 호출한다")
    void marksFailedAttemptOnFeignException() {
        UUID batchId = UUID.randomUUID();
        FinalSettlementPayout payout = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 900L, 1_000_000L);
        when(payoutWriter.findPendingPayouts(batchId)).thenReturn(List.of(payout));
        when(walletServiceClient.depositSettlement(any())).thenThrow(mock(FeignException.class));

        finalSettlementDisbursementService.disburse(batchId);

        verify(payoutWriter).markFailedAttempt(payout.getId());
        verify(payoutWriter, never()).markPaid(any());
        verify(payoutWriter).updateBatchStatus(batchId);
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지 건은 계속 처리한다")
    void continuesProcessingRemainingPayoutsAfterOneFailure() {
        UUID batchId = UUID.randomUUID();
        FinalSettlementPayout failing = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 900L, 1_000_000L);
        FinalSettlementPayout succeeding = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 900L, 1_000_000L);
        when(payoutWriter.findPendingPayouts(batchId)).thenReturn(List.of(failing, succeeding));
        SettlementDepositResponse response = new SettlementDepositResponse(9013L, 55L, "SETTLEMENT", succeeding.getAmount(), Instant.now(), null);
        when(walletServiceClient.depositSettlement(any()))
                .thenThrow(mock(FeignException.class))
                .thenReturn(ApiResponse.success(response, null));

        finalSettlementDisbursementService.disburse(batchId);

        verify(payoutWriter).markFailedAttempt(failing.getId());
        verify(payoutWriter).markPaid(succeeding.getId());
        verify(payoutWriter).updateBatchStatus(batchId);
    }

    @Test
    @DisplayName("존재하지 않는 최종 정산 회차면 markDisbursing에서 던진 예외가 그대로 전파되고, 지갑 호출은 일어나지 않는다")
    void propagatesExceptionWhenBatchNotFound() {
        UUID batchId = UUID.randomUUID();
        RuntimeException notFound = new RuntimeException("batch not found");
        doThrow(notFound).when(payoutWriter).markDisbursing(batchId);

        assertThatThrownBy(() -> finalSettlementDisbursementService.disburse(batchId))
                .isSameAs(notFound);

        verify(walletServiceClient, never()).depositSettlement(any());
        verify(payoutWriter, never()).findPendingPayouts(any());
    }
}
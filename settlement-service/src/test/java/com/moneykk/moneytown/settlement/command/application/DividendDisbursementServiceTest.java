package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.DividendDepositRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.DividendDepositResponse;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class DividendDisbursementServiceTest {

    @Mock
    private DividendPayoutWriter payoutWriter;
    @Mock
    private WalletServiceClient walletServiceClient;

    @InjectMocks
    private DividendDisbursementService dividendDisbursementService;

    @Test
    @DisplayName("markDisbursing → 지갑 호출 → markPaid → updateBatchStatus 순서로 처리하고, 각 단계는 건별로 커밋된다")
    void disbursesInOrderPerPayout() {
        UUID batchId = UUID.randomUUID();
        DividendPayout payout = DividendPayout.queue(batchId, UUID.randomUUID(), BigDecimal.ONE, 1_000_000L);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of(payout));
        DividendDepositResponse response = new DividendDepositResponse(9012L, 55L, "DIVIDEND", payout.getAmount(), Instant.now(), null);
        when(walletServiceClient.depositDividend(any())).thenReturn(ApiResponse.success(response, null));

        dividendDisbursementService.disburse(batchId);

        InOrder order = inOrder(payoutWriter, walletServiceClient);
        order.verify(payoutWriter).markDisbursing(batchId);
        order.verify(walletServiceClient).depositDividend(any());
        order.verify(payoutWriter).markPaid(payout.getId());
        order.verify(payoutWriter).updateBatchStatus(batchId);
        verify(payoutWriter, never()).markFailedAttempt(any());

        ArgumentCaptor<DividendDepositRequest> requestCaptor = ArgumentCaptor.forClass(DividendDepositRequest.class);
        verify(walletServiceClient).depositDividend(requestCaptor.capture());
        assertThat(requestCaptor.getValue().idempotencyKey()).isEqualTo(payout.getId().toString());
        assertThat(requestCaptor.getValue().investorId()).isEqualTo(payout.getInvestorId());
        assertThat(requestCaptor.getValue().settlementBatchId()).isEqualTo(batchId);
        assertThat(requestCaptor.getValue().amount()).isEqualTo(payout.getAmount());
    }

    @Test
    @DisplayName("지갑 응답이 success=false면 예외가 없어도 markPaid 대신 markFailedAttempt를 호출한다")
    void marksFailedAttemptWhenResponseSuccessIsFalse() {
        UUID batchId = UUID.randomUUID();
        DividendPayout payout = DividendPayout.queue(batchId, UUID.randomUUID(), BigDecimal.ONE, 1_000_000L);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of(payout));
        when(walletServiceClient.depositDividend(any()))
                .thenReturn(new ApiResponse<>(false, null, "지갑 처리 실패", "WALLET_500_01"));

        dividendDisbursementService.disburse(batchId);

        verify(payoutWriter).markFailedAttempt(payout.getId());
        verify(payoutWriter, never()).markPaid(any());
    }

    @Test
    @DisplayName("지갑 호출이 FeignException을 던지면 markPaid 대신 markFailedAttempt를 호출한다")
    void marksFailedAttemptOnFeignException() {
        UUID batchId = UUID.randomUUID();
        DividendPayout payout = DividendPayout.queue(batchId, UUID.randomUUID(), BigDecimal.ONE, 1_000_000L);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of(payout));
        when(walletServiceClient.depositDividend(any())).thenThrow(mock(FeignException.class));

        dividendDisbursementService.disburse(batchId);

        verify(payoutWriter).markFailedAttempt(payout.getId());
        verify(payoutWriter, never()).markPaid(any());
        verify(payoutWriter).updateBatchStatus(batchId);
    }

    @Test
    @DisplayName("FeignException이 아닌 예외가 나도 claim된 건이 PROCESSING에 갇히지 않도록 markFailedAttempt를 호출한다")
    void marksFailedAttemptOnUnexpectedException() {
        UUID batchId = UUID.randomUUID();
        DividendPayout payout = DividendPayout.queue(batchId, UUID.randomUUID(), BigDecimal.ONE, 1_000_000L);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of(payout));
        when(walletServiceClient.depositDividend(any())).thenThrow(new RuntimeException("unexpected"));

        dividendDisbursementService.disburse(batchId);

        verify(payoutWriter).markFailedAttempt(payout.getId());
        verify(payoutWriter, never()).markPaid(any());
        verify(payoutWriter).updateBatchStatus(batchId);
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지 건은 계속 처리한다")
    void continuesProcessingRemainingPayoutsAfterOneFailure() {
        UUID batchId = UUID.randomUUID();
        DividendPayout failing = DividendPayout.queue(batchId, UUID.randomUUID(), BigDecimal.ONE, 1_000_000L);
        DividendPayout succeeding = DividendPayout.queue(batchId, UUID.randomUUID(), BigDecimal.ONE, 1_000_000L);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of(failing, succeeding));
        DividendDepositResponse response = new DividendDepositResponse(9012L, 55L, "DIVIDEND", succeeding.getAmount(), Instant.now(), null);
        when(walletServiceClient.depositDividend(any()))
                .thenThrow(mock(FeignException.class))
                .thenReturn(ApiResponse.success(response, null));

        dividendDisbursementService.disburse(batchId);

        verify(payoutWriter).markFailedAttempt(failing.getId());
        verify(payoutWriter).markPaid(succeeding.getId());
        verify(payoutWriter).updateBatchStatus(batchId);
    }

    @Test
    @DisplayName("존재하지 않는 정산 회차면 markDisbursing에서 던진 예외가 그대로 전파되고, 지갑 호출은 일어나지 않는다")
    void propagatesExceptionWhenBatchNotFound() {
        UUID batchId = UUID.randomUUID();
        RuntimeException notFound = new RuntimeException("batch not found");
        doThrow(notFound).when(payoutWriter).markDisbursing(batchId);

        assertThatThrownBy(() -> dividendDisbursementService.disburse(batchId))
                .isSameAs(notFound);

        verify(walletServiceClient, never()).depositDividend(any());
        verify(payoutWriter, never()).claimPendingPayouts(any());
    }

    @Test
    @DisplayName("reclaimStalledProcessing: payoutWriter로 위임하고 결과를 그대로 반환한다")
    void reclaimStalledProcessingDelegatesToPayoutWriter() {
        Instant staleBefore = Instant.now();
        when(payoutWriter.reclaimStalledProcessing(staleBefore)).thenReturn(2);

        int reclaimed = dividendDisbursementService.reclaimStalledProcessing(staleBefore);

        assertThat(reclaimed).isEqualTo(2);
        verify(payoutWriter).reclaimStalledProcessing(staleBefore);
    }
}
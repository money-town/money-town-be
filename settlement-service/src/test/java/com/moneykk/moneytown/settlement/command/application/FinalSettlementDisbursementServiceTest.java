package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetServiceClient;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalSettlementDisbursementServiceTest {

    @Mock
    private FinalSettlementPayoutWriter payoutWriter;
    @Mock
    private WalletServiceClient walletServiceClient;
    @Mock
    private AssetServiceClient assetServiceClient;

    @InjectMocks
    private FinalSettlementDisbursementService finalSettlementDisbursementService;

    @Test
    @DisplayName("markDisbursing → 지갑 호출 → markPaid → updateBatchStatus 순서로 처리하고, 각 단계는 건별로 커밋된다")
    void disbursesInOrderPerPayout() {
        UUID batchId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        FinalSettlementPayout payout = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 900L, 1_000_000L);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of(payout));
        when(payoutWriter.updateBatchStatus(batchId))
                .thenReturn(Optional.of(assetId));
        SettlementDepositResponse response = new SettlementDepositResponse(9013L, 55L, "SETTLEMENT", payout.getAmount(), batchId, Instant.now());
        when(walletServiceClient.depositSettlement(any())).thenReturn(ApiResponse.success(response, null));

        finalSettlementDisbursementService.disburse(batchId);

        InOrder order = inOrder(
                payoutWriter,
                walletServiceClient,
                assetServiceClient
        );
        order.verify(payoutWriter).markDisbursing(batchId);
        order.verify(walletServiceClient).depositSettlement(any());
        order.verify(payoutWriter).markPaid(payout.getId());
        order.verify(payoutWriter).updateBatchStatus(batchId);
        order.verify(assetServiceClient)
                .completeAssetTermination(assetId, "SYSTEM");
        order.verify(payoutWriter).markAssetTerminationCompleted(eq(batchId), any(Instant.class));
        verify(payoutWriter, never()).markFailedAttempt(any());

        ArgumentCaptor<SettlementDepositRequest> requestCaptor = ArgumentCaptor.forClass(SettlementDepositRequest.class);
        verify(walletServiceClient).depositSettlement(requestCaptor.capture());
        assertThat(requestCaptor.getValue().idempotencyKey()).isEqualTo(payout.getId().toString());
        assertThat(requestCaptor.getValue().investorId()).isEqualTo(payout.getInvestorId());
        assertThat(requestCaptor.getValue().finalSettlementBatchId()).isEqualTo(batchId);
        assertThat(requestCaptor.getValue().amount()).isEqualTo(payout.getAmount());
    }

    @Test
    @DisplayName("자산 서비스 종료 완료 통보가 실패해도 예외를 전파하지 않고, 완료 시각도 저장하지 않는다")
    void doesNotSaveCompletedAtWhenAssetServiceNotificationFails() {
        UUID batchId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of());
        when(payoutWriter.updateBatchStatus(batchId)).thenReturn(Optional.of(assetId));
        doThrow(mock(FeignException.class)).when(assetServiceClient).completeAssetTermination(assetId, "SYSTEM");

        finalSettlementDisbursementService.disburse(batchId);

        verify(payoutWriter, never()).markAssetTerminationCompleted(any(), any());
    }

    @Test
    @DisplayName("retryPendingAssetTerminationNotifications: 통보 대기 중인 COMPLETED 회차를 재호출해 성공하면 완료 시각을 저장한다")
    void retriesPendingAssetTerminationNotifications() {
        UUID batchId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        FinalSettlementBatch batch = FinalSettlementBatch.open(assetId, Instant.now(), 1_000_000L, 900_000_000L);
        ReflectionTestUtils.setField(batch, "id", batchId);
        when(payoutWriter.findCompletedBatchesPendingTerminationNotification()).thenReturn(List.of(batch));

        finalSettlementDisbursementService.retryPendingAssetTerminationNotifications();

        verify(assetServiceClient).completeAssetTermination(assetId, "SYSTEM");
        verify(payoutWriter).markAssetTerminationCompleted(eq(batchId), any(Instant.class));
    }

    @Test
    @DisplayName("retryPendingAssetTerminationNotifications: 한 회차가 실패해도 나머지 회차는 계속 재호출한다")
    void continuesRetryingRemainingBatchesAfterOneFailure() {
        UUID failingAssetId = UUID.randomUUID();
        UUID succeedingAssetId = UUID.randomUUID();
        FinalSettlementBatch failingBatch = FinalSettlementBatch.open(failingAssetId, Instant.now(), 1_000_000L, 900_000_000L);
        FinalSettlementBatch succeedingBatch = FinalSettlementBatch.open(succeedingAssetId, Instant.now(), 1_000_000L, 900_000_000L);
        when(payoutWriter.findCompletedBatchesPendingTerminationNotification())
                .thenReturn(List.of(failingBatch, succeedingBatch));
        doThrow(mock(FeignException.class)).when(assetServiceClient).completeAssetTermination(failingAssetId, "SYSTEM");

        finalSettlementDisbursementService.retryPendingAssetTerminationNotifications();

        verify(assetServiceClient).completeAssetTermination(succeedingAssetId, "SYSTEM");
        verify(payoutWriter, times(1)).markAssetTerminationCompleted(eq(succeedingBatch.getId()), any(Instant.class));
        verify(payoutWriter, never()).markAssetTerminationCompleted(eq(failingBatch.getId()), any());
    }

    @Test
    @DisplayName("지갑 응답이 success=false면 예외가 없어도 markPaid 대신 markFailedAttempt를 호출한다")
    void marksFailedAttemptWhenResponseSuccessIsFalse() {
        UUID batchId = UUID.randomUUID();
        FinalSettlementPayout payout = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 900L, 1_000_000L);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of(payout));
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
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of(payout));
        when(walletServiceClient.depositSettlement(any())).thenThrow(mock(FeignException.class));

        finalSettlementDisbursementService.disburse(batchId);

        verify(payoutWriter).markFailedAttempt(payout.getId());
        verify(payoutWriter, never()).markPaid(any());
        verify(payoutWriter).updateBatchStatus(batchId);
    }

    @Test
    @DisplayName("FeignException이 아닌 예외가 나도 claim된 건이 PROCESSING에 갇히지 않도록 markFailedAttempt를 호출한다")
    void marksFailedAttemptOnUnexpectedException() {
        UUID batchId = UUID.randomUUID();
        FinalSettlementPayout payout = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 900L, 1_000_000L);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of(payout));
        when(walletServiceClient.depositSettlement(any())).thenThrow(new RuntimeException("unexpected"));

        finalSettlementDisbursementService.disburse(batchId);

        verify(payoutWriter).markFailedAttempt(payout.getId());
        verify(payoutWriter, never()).markPaid(any());
        verify(payoutWriter).updateBatchStatus(batchId);
    }

    @Test
    @DisplayName("지갑 응답의 finalSettlementBatchId가 요청과 다르면 markPaid 대신 즉시 markResponseMismatch를 호출한다")
    void marksResponseMismatchWhenFinalSettlementBatchIdDiffers() {
        UUID batchId = UUID.randomUUID();
        UUID otherBatchId = UUID.randomUUID();
        FinalSettlementPayout payout = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 900L, 1_000_000L);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of(payout));
        SettlementDepositResponse response = new SettlementDepositResponse(9013L, 55L, "SETTLEMENT", payout.getAmount(), otherBatchId, Instant.now());
        when(walletServiceClient.depositSettlement(any())).thenReturn(ApiResponse.success(response, null));

        finalSettlementDisbursementService.disburse(batchId);

        verify(payoutWriter).markResponseMismatch(payout.getId());
        verify(payoutWriter, never()).markPaid(any());
        verify(payoutWriter, never()).markFailedAttempt(any());
        verify(payoutWriter).updateBatchStatus(batchId);
    }

    @Test
    @DisplayName("지갑 응답이 success=true인데 data가 없으면 불일치로 간주해 즉시 markResponseMismatch를 호출한다")
    void marksResponseMismatchWhenDataIsNull() {
        UUID batchId = UUID.randomUUID();
        FinalSettlementPayout payout = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 900L, 1_000_000L);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of(payout));
        when(walletServiceClient.depositSettlement(any())).thenReturn(ApiResponse.success(null, null));

        finalSettlementDisbursementService.disburse(batchId);

        verify(payoutWriter).markResponseMismatch(payout.getId());
        verify(payoutWriter, never()).markPaid(any());
        verify(payoutWriter, never()).markFailedAttempt(any());
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지 건은 계속 처리한다")
    void continuesProcessingRemainingPayoutsAfterOneFailure() {
        UUID batchId = UUID.randomUUID();
        FinalSettlementPayout failing = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 900L, 1_000_000L);
        FinalSettlementPayout succeeding = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 900L, 1_000_000L);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of(failing, succeeding));
        SettlementDepositResponse response = new SettlementDepositResponse(9013L, 55L, "SETTLEMENT", succeeding.getAmount(), batchId, Instant.now());
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
        verify(payoutWriter, never()).claimPendingPayouts(any());
    }

    @Test
    @DisplayName("reclaimStalledProcessing: payoutWriter로 위임하고 결과를 그대로 반환한다")
    void reclaimStalledProcessingDelegatesToPayoutWriter() {
        Instant staleBefore = Instant.now();
        when(payoutWriter.reclaimStalledProcessing(staleBefore)).thenReturn(2);

        int reclaimed = finalSettlementDisbursementService.reclaimStalledProcessing(staleBefore);

        assertThat(reclaimed).isEqualTo(2);
        verify(payoutWriter).reclaimStalledProcessing(staleBefore);
    }
}

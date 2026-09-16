package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.SettlementFailureNotifier;
import com.moneykk.moneytown.settlement.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.SettlementDepositRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.SettlementDepositResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinalSettlementDisbursementService {

    private static final String SYSTEM_ROLE = "SYSTEM";
    private static final List<SettlementStatus> FAILURE_STATUSES = List.of(SettlementStatus.FAILED, SettlementStatus.PARTIAL_FAILED);

    private final FinalSettlementPayoutWriter payoutWriter;
    private final WalletServiceClient walletServiceClient;
    private final AssetServiceClient assetServiceClient;
    private final SettlementFailureNotifier settlementFailureNotifier;

    @Async("disbursementTaskExecutor")
    public void disburseAsync(UUID finalSettlementBatchId) {
        disburse(finalSettlementBatchId);
    }

    public void disburse(UUID finalSettlementBatchId) {
        payoutWriter.markDisbursing(finalSettlementBatchId);

        List<FinalSettlementPayout> claimedPayouts = payoutWriter.claimPendingPayouts(finalSettlementBatchId);
        log.info("최종 정산 지급 처리 시작 (finalSettlementBatchId={}, 대상 건수={})", finalSettlementBatchId, claimedPayouts.size());
        claimedPayouts.forEach(payout -> attempt(finalSettlementBatchId, payout));

        payoutWriter.updateBatchStatus(finalSettlementBatchId).ifPresentOrElse(
                batch -> {
                    log.info("최종 정산 지급 처리 마감 (finalSettlementBatchId={}, status={})", finalSettlementBatchId, batch.getStatus());
                    if (batch.getStatus() == SettlementStatus.COMPLETED) {
                        notifyAssetTerminationCompleted(finalSettlementBatchId, batch.getAssetId());
                    } else if (FAILURE_STATUSES.contains(batch.getStatus())) {
                        settlementFailureNotifier.notifyFinalSettlementBatchFailed(batch);
                    }
                },
                () -> log.debug("최종 정산 지급 처리 중 — 아직 진행 중인 건이 남아있어 회차 상태를 확정하지 않음 (finalSettlementBatchId={})",
                        finalSettlementBatchId)
        );
    }

    // COMPLETED로 확정됐지만 자산 서비스 통보에 아직 성공하지 못한 회차를 재호출한다. 자산 서비스 API는 멱등하다.
    public void retryPendingAssetTerminationNotifications() {
        List<FinalSettlementBatch> pending = payoutWriter.findCompletedBatchesPendingTerminationNotification();
        if (pending.isEmpty()) {
            return;
        }
        log.info("자산 종료 완료 통보가 아직 안 된 최종 정산 회차 {}건을 재통보합니다.", pending.size());
        pending.forEach(batch -> notifyAssetTerminationCompleted(batch.getId(), batch.getAssetId()));
    }

    public int reclaimStalledProcessing(Instant staleBefore) {
        return payoutWriter.reclaimStalledProcessing(staleBefore);
    }

    // 완료 시각은 자산 서비스 호출 성공 후에만 저장한다. 실패하면 NULL로 남겨 두고 스케줄러가 재시도한다.
    private void notifyAssetTerminationCompleted(UUID finalSettlementBatchId, UUID assetId) {
        try {
            assetServiceClient.completeAssetTermination(assetId, SYSTEM_ROLE);
            payoutWriter.markAssetTerminationCompleted(finalSettlementBatchId, Instant.now());
        } catch (Exception e) {
            log.warn("자산 서비스에 종료 완료 통보 실패 (finalSettlementBatchId={}, assetId={})", finalSettlementBatchId, assetId, e);
        }
    }

    private void attempt(UUID finalSettlementBatchId, FinalSettlementPayout payout) {
        try {
            ApiResponse<SettlementDepositResponse> response = walletServiceClient.depositSettlement(new SettlementDepositRequest(
                    payout.getId().toString(), payout.getInvestorId(), finalSettlementBatchId, payout.getAmount()));
            if (!response.success()) {
                payoutWriter.markFailedAttempt(payout.getId());
                return;
            }

            SettlementDepositResponse data = response.data();
            if (data == null || !finalSettlementBatchId.equals(data.finalSettlementBatchId())) {
                // TODO 도전 기능 = 별도 상태/플래그를 둬서 재처리 API가 구분 -> 지갑 트랜잭션 대조 확인 후에만 재처리
                // 일반 DEAD_LETTER와 같은 재처리 경로(retryFinalSettlement) -> 사람이 로그를 못 보고 재처리 버튼을 누르면 대조 확인 없이 재시도 가능
                log.error("지갑 응답의 finalSettlementBatchId가 요청과 다릅니다 — 재처리 전 지갑 트랜잭션 대조 확인 필요. "
                                + "payoutId={}, 요청 finalSettlementBatchId={}, 응답 finalSettlementBatchId={}, transactionId={}",
                        payout.getId(), finalSettlementBatchId, data == null ? null : data.finalSettlementBatchId(),
                        data == null ? null : data.transactionId());
                payoutWriter.markResponseMismatch(payout.getId());
                return;
            }

            payoutWriter.markPaid(payout.getId());
        } catch (Exception e) {
            log.error("최종 정산 지급 처리 중 예외 발생. payoutId={}", payout.getId(), e);
            payoutWriter.markFailedAttempt(payout.getId());
        }
    }
}

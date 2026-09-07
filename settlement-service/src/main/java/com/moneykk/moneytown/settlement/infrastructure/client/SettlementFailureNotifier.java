package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.NotificationRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementFailureNotifier {

    private final AnalysisServiceClient analysisServiceClient;

    // 회차ID를 멱등키로 써서, 같은 회차가 재시도로 여러 번 FAILED를 거쳐도 analysis-service가 알림을 회차당 1회로 걸러낸다.
    public void notifyDividendBatchFailed(SettlementBatch batch) {
        notify(batch.getId(), "배당 정산 실패",
                "정산 회차가 %s 상태로 종료됐습니다. settlementBatchId=%s, assetId=%s"
                        .formatted(batch.getStatus(), batch.getId(), batch.getAssetId()));
    }

    public void notifyFinalSettlementBatchFailed(FinalSettlementBatch batch) {
        notify(batch.getId(), "최종 정산 실패",
                "최종 정산 회차가 %s 상태로 종료됐습니다. finalSettlementBatchId=%s, assetId=%s"
                        .formatted(batch.getStatus(), batch.getId(), batch.getAssetId()));
    }

    private void notify(UUID batchId, String title, String message) {
        try {
            analysisServiceClient.sendNotification(batchId, new NotificationRequest(NotificationType.SETTLEMENT_FAILED, null, title, message));
        } catch (Exception e) {
            log.warn("정산 실패 알림 전송 실패 (batchId={})", batchId, e);
        }
    }
}
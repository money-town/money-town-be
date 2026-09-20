package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.NotificationRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

    // 실패 상태로 멈춘 회차가 자산의 다음 정산을 막고 있음을 알린다 (T5). 폴링이 30분마다 반복되므로
    // 멱등키에 날짜를 섞어 같은 회차라도 하루 1회만 나가게 하고, 회차 자체의 실패 알림(멱등키=batchId)과는 키를 분리한다.
    public void notifyAssetBlockedByFailedBatch(SettlementBatch batch, UUID waitingRevenueId, LocalDate today) {
        UUID idempotencyKey = UUID.nameUUIDFromBytes(("asset-blocked:" + batch.getId() + ":" + today).getBytes(StandardCharsets.UTF_8));
        notify(idempotencyKey, "정산 회차 실패로 자산의 다음 정산이 차단됨",
                "실패 상태 회차가 자산의 신규 정산을 막고 있습니다. 실패 건을 재시도하거나 포기 처리해야 합니다. "
                        + "settlementBatchId=%s, assetId=%s, status=%s, 대기 중 revenueId=%s"
                        .formatted(batch.getId(), batch.getAssetId(), batch.getStatus(), waitingRevenueId));
    }

    private void notify(UUID idempotencyKey, String title, String message) {
        try {
            analysisServiceClient.sendNotification(idempotencyKey, new NotificationRequest(NotificationType.SETTLEMENT_FAILED, null, title, message));
        } catch (Exception e) {
            log.warn("정산 실패 알림 전송 실패 (idempotencyKey={})", idempotencyKey, e);
        }
    }
}
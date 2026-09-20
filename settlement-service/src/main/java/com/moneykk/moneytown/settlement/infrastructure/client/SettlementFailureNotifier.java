package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.NotificationRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.NotificationResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementFailureNotifier {

    // 미해결 알림의 "하루"는 KST 기준 — UTC로 하면 오전 9시에 날짜가 바뀌어 알림 시점이 어긋난다.
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 회차가 실패로 확정된 직후에는 최초 알림(disburse가 즉시 발송)에 맡기고, 이 시간이 지나도 실패 상태면 재확인한다.
    private static final Duration INITIAL_ALERT_GRACE = Duration.ofHours(1);
    // 이 일수 이상 방치되면 제목을 바꿔 눈에 띄게 한다. 알림을 멈추지는 않는다 — 방치된 회차는 자산 차단이 계속되므로.
    private static final long ESCALATE_AFTER_DAYS = 7;
    private static final String FAILED_STATUS = "FAILED";

    private final AnalysisServiceClient analysisServiceClient;

    // 회차ID를 멱등키로 써서, 같은 회차가 재시도로 여러 번 FAILED를 거쳐도 알림을 회차당 1회로 걸러낸다.
    public void notifyDividendBatchFailed(SettlementBatch batch) {
        sendQuietly(batch.getId(), dividendInitialRequest(batch));
    }

    public void notifyFinalSettlementBatchFailed(FinalSettlementBatch batch) {
        sendQuietly(batch.getId(), finalSettlementInitialRequest(batch));
    }

    // 실패 상태로 남은 회차를 재통보한다. 스케줄러 스캔과 폴링의 차단 감지가 같은 경로·같은 멱등키를 쓰므로
    // 같은 회차에 대해 하루 1건을 넘지 않는다. waitingRevenueId는 이 회차 때문에 대기 중인 수익(없으면 null).
    public void remindUnresolvedDividendBatch(SettlementBatch batch, UUID waitingRevenueId) {
        remindUnresolvedDividendBatch(batch, waitingRevenueId, Instant.now());
    }

    void remindUnresolvedDividendBatch(SettlementBatch batch, UUID waitingRevenueId, Instant now) {
        remind(batch.getId(), dividendInitialRequest(batch), "배당 정산", batch.getStatus().name(),
                batch.getAssetId(), batch.getUpdatedAt(), waitingRevenueId, now);
    }

    public void remindUnresolvedFinalSettlementBatch(FinalSettlementBatch batch) {
        remindUnresolvedFinalSettlementBatch(batch, Instant.now());
    }

    void remindUnresolvedFinalSettlementBatch(FinalSettlementBatch batch, Instant now) {
        remind(batch.getId(), finalSettlementInitialRequest(batch), "최종 정산", batch.getStatus().name(),
                batch.getAssetId(), batch.getUpdatedAt(), null, now);
    }

    // 1) 최초 알림을 같은 멱등키로 다시 호출한다. analysis는 기존 레코드가 있으면 현재 상태(PENDING/SENT/FAILED)를 그대로
    //    돌려주고, 아예 없으면(최초 호출이 analysis에 닿지 못한 경우) 그 자리에서 최초 알림으로 발송한다 — 중복 없이 복구
    // 2) 최초 알림이 전달됐어도 회차가 하루 넘게 미해결이면 날짜 키로 재통보한다(retryBatch 후 재실패는 batchId 키에 막혀 조용하다).
    // 3) 최초 알림이 FAILED거나 확인 자체가 실패했으면 날짜 키 알림을 바로 보낸다 — 최대 하루 침묵하지 않게.
    private void remind(UUID batchId, NotificationRequest initialRequest, String label, String status, UUID assetId,
                        Instant stuckSince, UUID waitingRevenueId, Instant now) {
        if (stuckSince == null || stuckSince.isAfter(now.minus(INITIAL_ALERT_GRACE))) {
            return;
        }

        boolean initialDelivered = isInitialAlertDelivered(batchId, initialRequest);
        long stuckDays = Duration.between(stuckSince, now).toDays();
        if (initialDelivered && stuckDays < 1) {
            return;
        }

        UUID dailyKey = dailyReminderKey(batchId, now);
        long dayNumber = stuckDays + 1;
        String title = stuckDays >= ESCALATE_AFTER_DAYS
                ? "[장기 미해결] %s 회차 실패 %d일째 방치".formatted(label, dayNumber)
                : "%s 회차 실패 미해결".formatted(label);
        String message = ("%s 회차가 %s 상태로 %d일째 해결되지 않았습니다. 실패 건을 재시도하거나 포기 처리해야 합니다. batchId=%s, assetId=%s"
                .formatted(label, status, dayNumber, batchId, assetId))
                + (waitingRevenueId == null ? "" : " — 이 회차 때문에 대기 중인 revenueId=%s (자산의 다음 정산이 차단됨)".formatted(waitingRevenueId));
        sendQuietly(dailyKey, new NotificationRequest(NotificationType.SETTLEMENT_FAILED, null, title, message));
    }

    private boolean isInitialAlertDelivered(UUID batchId, NotificationRequest initialRequest) {
        try {
            NotificationResponse response = analysisServiceClient.sendNotification(batchId, initialRequest).data();
            return response != null && !FAILED_STATUS.equals(response.status());
        } catch (Exception e) {
            log.warn("최초 정산 실패 알림 상태 확인 실패 (batchId={})", batchId, e);
            return false;
        }
    }

    private UUID dailyReminderKey(UUID batchId, Instant now) {
        String key = "failed-batch-reminder:" + batchId + ":" + now.atZone(SEOUL).toLocalDate();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private NotificationRequest dividendInitialRequest(SettlementBatch batch) {
        return new NotificationRequest(NotificationType.SETTLEMENT_FAILED, null, "배당 정산 실패",
                "정산 회차가 %s 상태로 종료됐습니다. settlementBatchId=%s, assetId=%s"
                        .formatted(batch.getStatus(), batch.getId(), batch.getAssetId()));
    }

    private NotificationRequest finalSettlementInitialRequest(FinalSettlementBatch batch) {
        return new NotificationRequest(NotificationType.SETTLEMENT_FAILED, null, "최종 정산 실패",
                "최종 정산 회차가 %s 상태로 종료됐습니다. finalSettlementBatchId=%s, assetId=%s"
                        .formatted(batch.getStatus(), batch.getId(), batch.getAssetId()));
    }

    private void sendQuietly(UUID idempotencyKey, NotificationRequest request) {
        try {
            analysisServiceClient.sendNotification(idempotencyKey, request);
        } catch (Exception e) {
            log.warn("정산 실패 알림 전송 실패 (idempotencyKey={})", idempotencyKey, e);
        }
    }
}

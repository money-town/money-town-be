package com.moneykk.moneytown.analysis.notification.query.dto;

import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import com.moneykk.moneytown.analysis.notification.domain.NotificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record NotificationSearchCondition(
        @Schema(description = "알림 유형 필터 (선택)")
        NotificationType notificationType,
        @Schema(description = "발송 상태 필터 (선택)")
        NotificationStatus status,
        @Schema(description = "수신 사용자 ID 필터 (선택)")
        UUID userId
) {
}

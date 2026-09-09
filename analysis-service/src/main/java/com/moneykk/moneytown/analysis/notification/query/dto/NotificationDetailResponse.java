package com.moneykk.moneytown.analysis.notification.query.dto;

import com.moneykk.moneytown.analysis.notification.domain.Notification;
import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import com.moneykk.moneytown.analysis.notification.domain.NotificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record NotificationDetailResponse(
        @Schema(description = "알림 ID")
        UUID notificationId,
        @Schema(description = "알림 유형")
        NotificationType notificationType,
        @Schema(description = "알림 제목")
        String title,
        @Schema(description = "알림 내용")
        String message,
        @Schema(description = "발송 상태")
        NotificationStatus status,
        @Schema(description = "수신 사용자 ID (운영 채널 알림이면 null)")
        UUID userId,
        @Schema(description = "발송 시각 (미발송 시 null)")
        Instant sentAt,
        @Schema(description = "실패 사유 (실패 시에만 존재)")
        String errorMessage,
        @Schema(description = "생성 시각")
        Instant createdAt
) {
    public static NotificationDetailResponse from(Notification notification){
        return new NotificationDetailResponse(notification.getId(), notification.getNotificationType(),
                notification.getTitle(), notification.getMessage(), notification.getStatus(), notification.getUserId(),
                notification.getSentAt(),notification.getErrorMessage(),notification.getCreatedAt());
    }
}

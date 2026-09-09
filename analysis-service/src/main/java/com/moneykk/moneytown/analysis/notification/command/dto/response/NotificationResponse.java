package com.moneykk.moneytown.analysis.notification.command.dto.response;

import com.moneykk.moneytown.analysis.notification.domain.Notification;
import com.moneykk.moneytown.analysis.notification.domain.NotificationStatus;
import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        @Schema(description = "알림 ID")
        UUID notificationId,
        @Schema(description = "알림 유형")
        NotificationType notificationType,
        @Schema(description = "발송 상태")
        NotificationStatus status,
        @Schema(description = "발송 시각 (미발송 시 null)")
        Instant sentAt
) {
    public static NotificationResponse from(Notification notification){
        return new NotificationResponse(notification.getId(), notification.getNotificationType(), notification.getStatus(), notification.getSentAt());
    }
}

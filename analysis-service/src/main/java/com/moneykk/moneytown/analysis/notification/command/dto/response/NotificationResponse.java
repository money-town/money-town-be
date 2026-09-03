package com.moneykk.moneytown.analysis.notification.command.dto.response;

import com.moneykk.moneytown.analysis.notification.domain.Notification;
import com.moneykk.moneytown.analysis.notification.domain.NotificationStatus;
import com.moneykk.moneytown.analysis.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID notificationId,
        NotificationType notificationType,
        NotificationStatus status,
        Instant sentAt
) {
    public static NotificationResponse from(Notification notification){
        return new NotificationResponse(notification.getId(), notification.getNotificationType(), notification.getStatus(), notification.getSentAt());
    }
}

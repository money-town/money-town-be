package com.moneykk.moneytown.analysis.notification.query.dto;

import com.moneykk.moneytown.analysis.notification.domain.Notification;
import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import com.moneykk.moneytown.analysis.notification.domain.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationListItemResponse(
        UUID notificationId,
        NotificationType notificationType,
        String title,
        NotificationStatus status,
        UUID userId,
        Instant createdAt
) {
    public static NotificationListItemResponse from(Notification notification){
        return new NotificationListItemResponse(notification.getId(), notification.getNotificationType(),
                notification.getTitle(), notification.getStatus(), notification.getUserId(), notification.getCreatedAt());
    }
}

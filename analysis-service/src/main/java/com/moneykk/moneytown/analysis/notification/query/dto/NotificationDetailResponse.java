package com.moneykk.moneytown.analysis.notification.query.dto;

import com.moneykk.moneytown.analysis.notification.domain.Notification;
import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import com.moneykk.moneytown.analysis.notification.domain.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationDetailResponse(
        UUID notificationId,
        NotificationType notificationType,
        String title,
        String message,
        NotificationStatus status,
        UUID userId,
        Instant sentAt,
        String errorMessage,
        Instant createdAt
) {
    public static NotificationDetailResponse from(Notification notification){
        return new NotificationDetailResponse(notification.getId(), notification.getNotificationType(),
                notification.getTitle(), notification.getMessage(), notification.getStatus(), notification.getUserId(),
                notification.getSentAt(),notification.getErrorMessage(),notification.getCreatedAt());
    }
}

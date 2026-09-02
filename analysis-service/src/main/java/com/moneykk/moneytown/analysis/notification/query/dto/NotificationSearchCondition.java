package com.moneykk.moneytown.analysis.notification.query.dto;

import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import com.moneykk.moneytown.analysis.notification.domain.NotificationStatus;

import java.util.UUID;

public record NotificationSearchCondition(
        NotificationType notificationType,
        NotificationStatus status,
        UUID userId
) {
}

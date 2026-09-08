package com.moneykk.moneytown.settlement.infrastructure.client.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID notificationId,
        String notificationType,
        String status,
        Instant sentAt
) {
}
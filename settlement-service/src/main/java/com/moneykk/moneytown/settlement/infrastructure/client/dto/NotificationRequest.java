package com.moneykk.moneytown.settlement.infrastructure.client.dto;

import java.util.UUID;

public record NotificationRequest(
        NotificationType notificationType,
        UUID userId,
        String title,
        String message
) {
}
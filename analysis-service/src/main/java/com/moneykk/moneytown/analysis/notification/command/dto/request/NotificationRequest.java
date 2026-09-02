package com.moneykk.moneytown.analysis.notification.command.dto.request;

import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record NotificationRequest(
        @NotNull NotificationType notificationType,
        UUID userId,                // null 은 운영채널 알림
        @NotBlank String title,
        @NotBlank String message
        ) {
}

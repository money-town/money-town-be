package com.moneykk.moneytown.analysis.notification.command.dto.request;

import jakarta.validation.constraints.NotBlank;

public record NotificationTestRequest(
        @NotBlank String title,
        @NotBlank String message
) {
}

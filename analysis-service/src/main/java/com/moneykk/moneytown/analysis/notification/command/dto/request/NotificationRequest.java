package com.moneykk.moneytown.analysis.notification.command.dto.request;

import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record NotificationRequest(

        @NotNull(message = "NotificationType은 필수 입니다.")
        NotificationType notificationType,
        UUID userId,                // null 은 운영채널 알림
        @NotBlank(message = "제목 입력은 필수 입니다.")
        String title,
        @NotBlank(message = "내용을 작성해주세요.")
        String message
        ) {
}

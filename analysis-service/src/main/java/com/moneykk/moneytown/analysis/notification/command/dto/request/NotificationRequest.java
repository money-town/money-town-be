package com.moneykk.moneytown.analysis.notification.command.dto.request;

import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record NotificationRequest(

        @Schema(description = "알림 유형 (필수)", example = "SETTLEMENT_FAILED")
        @NotNull(message = "NotificationType은 필수 입니다.")
        NotificationType notificationType,
        @Schema(description = "수신 사용자 ID. null이면 운영 채널 알림")
        UUID userId,                // null 은 운영채널 알림
        @Schema(description = "알림 제목 (필수)")
        @NotBlank(message = "제목 입력은 필수 입니다.")
        String title,
        @Schema(description = "알림 내용 (필수)")
        @NotBlank(message = "내용을 작성해주세요.")
        String message
        ) {
}

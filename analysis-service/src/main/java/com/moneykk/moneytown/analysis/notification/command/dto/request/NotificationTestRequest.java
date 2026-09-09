package com.moneykk.moneytown.analysis.notification.command.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record NotificationTestRequest(
        @Schema(description = "알림 제목 (필수)", example = "테스트 알림")
        @NotBlank(message = "제목을 입력해주세요.")
        String title,
        @Schema(description = "알림 내용 (필수)", example = "Slack 연동 확인용 메시지입니다.")
        @NotBlank(message = "내용을 입력해주세요.")
        String message
) {
}

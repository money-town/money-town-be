package com.moneykk.moneytown.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "발행자 권한 신청 거절 요청")
public record IssuerRejectRequest(
        @Schema(description = "거절 사유", example = "신청 정보가 충분하지 않습니다.")
        @NotBlank(message = "거절 사유는 필수입니다.")
        @Size(max = 500, message = "거절 사유는 500자 이하여야 합니다.")
        String rejectionReason
) {
}

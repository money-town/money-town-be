package com.moneykk.moneytown.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "발행자 권한 신청 요청")
public record IssuerApplyRequest(
        @Schema(description = "발행자 권한 신청 사유", example = "보유 자산의 공모를 진행하고 싶습니다.")
        @NotBlank(message = "신청 사유는 필수입니다.")
        @Size(max = 500, message = "신청 사유는 500자 이하여야 합니다.")
        String applicationReason
) {
}

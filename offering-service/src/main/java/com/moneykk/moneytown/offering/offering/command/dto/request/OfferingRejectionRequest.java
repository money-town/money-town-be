package com.moneykk.moneytown.offering.offering.command.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OfferingRejectionRequest(

        @Schema(
                description = "관리자가 공모 심사를 반려한 사유",
                example = "자산 증빙 자료가 충분하지 않아 공모를 승인할 수 없습니다."
        )
        @NotBlank(message = "반려 사유를 입력해주세요.")
        @Size(max = 500, message = "반려 사유는 500자를 초과할 수 없습니다.")
        String rejectionReason

) {
}
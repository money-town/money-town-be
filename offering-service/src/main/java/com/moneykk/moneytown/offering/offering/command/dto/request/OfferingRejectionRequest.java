package com.moneykk.moneytown.offering.offering.command.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OfferingRejectionRequest(

        @NotBlank(message = "반려 사유를 입력해주세요.")
        @Size(max = 500, message = "반려 사유는 500자를 초과할 수 없습니다.")
        String rejectionReason

) {
}
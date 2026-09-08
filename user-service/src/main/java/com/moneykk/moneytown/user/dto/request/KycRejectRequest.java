package com.moneykk.moneytown.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KycRejectRequest(@NotBlank(message = "거절 사유는 필수입니다.")
                               @Size(max = 500, message = "거절 사유는 500자 이하여야 합니다.")
                               String rejectionReason) {
}

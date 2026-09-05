package com.moneykk.moneytown.asset.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** 지분 회수 요청 */
public record HoldingRevocationRequest(

        @NotNull(message = "청약 ID는 필수입니다.")
        UUID subscriptionId,

        @NotBlank(message = "지분 회수 사유는 필수입니다.")
        @Size(max = 500, message = "지분 회수 사유는 500자 이하여야 합니다.")
        String reason
) {
}
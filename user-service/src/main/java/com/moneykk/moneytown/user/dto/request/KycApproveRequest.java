package com.moneykk.moneytown.user.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record KycApproveRequest(
        @NotNull(message = "KYC 만료 시각은 필수입니다.")
        @Future(message = "KYC 만료 시각은 현재 시각 이후여야 합니다.")
        Instant expiresAt
) {
}

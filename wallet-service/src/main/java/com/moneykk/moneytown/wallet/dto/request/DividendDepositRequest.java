package com.moneykk.moneytown.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

// settlement-service의 WalletServiceClient.depositDividend가 보내는 요청과 동일한 구조
public record DividendDepositRequest(
        @NotBlank(message = "idempotencyKey는 필수입니다.")
        String idempotencyKey,
        @NotNull(message = "investorId는 필수입니다.")
        UUID investorId,
        @NotNull(message = "settlementBatchId는 필수입니다.")
        UUID settlementBatchId,
        @NotNull(message = "금액은 필수입니다.")
        @Positive(message = "금액은 0보다 커야 합니다.")
        Long amount
) {
}

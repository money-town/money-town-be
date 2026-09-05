package com.moneykk.moneytown.wallet.dto.response;

import com.moneykk.moneytown.wallet.entity.Wallet;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record WalletResponse(
        @Schema(description = "지갑 ID") Long walletId,
        @Schema(description = "총 잔액") long balance,
        @Schema(description = "동결(청약금 HOLD) 금액") long holdBalance,
        @Schema(description = "가용 잔액 (총 잔액 - 동결 금액)") long availableBalance,
        @Schema(description = "마지막 갱신 시각") Instant updatedAt
) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getBalance(),
                wallet.getHoldBalance(),
                wallet.getAvailableBalance(),
                wallet.getUpdatedAt()
        );
    }
}

package com.moneykk.moneytown.wallet.dto.response;

import com.moneykk.moneytown.wallet.entity.Wallet;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record AdminWalletDetailResponse(
        @Schema(description = "지갑 ID") Long walletId,
        @Schema(description = "지갑 소유자 사용자 ID") UUID userId,
        @Schema(description = "총 잔액") long balance,
        @Schema(description = "동결(청약금 HOLD) 금액") long holdBalance,
        @Schema(description = "가용 잔액 (총 잔액 - 동결 금액)") long availableBalance,
        @Schema(description = "삭제(탈퇴) 여부") boolean isDeleted,
        @Schema(description = "생성 시각") Instant createdAt,
        @Schema(description = "마지막 갱신 시각") Instant updatedAt
) {
    public static AdminWalletDetailResponse from(Wallet wallet) {
        return new AdminWalletDetailResponse(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getBalance(),
                wallet.getHoldBalance(),
                wallet.getAvailableBalance(),
                wallet.isDeleted(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }
}

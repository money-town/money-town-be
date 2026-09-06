package com.moneykk.moneytown.wallet.dto.response;

import com.moneykk.moneytown.wallet.entity.Wallet;
import io.swagger.v3.oas.annotations.media.Schema;

public record WalletStatusResponse(
        @Schema(description = "지갑 ID") Long walletId,
        @Schema(description = "동결(청약금 HOLD) 금액") long holdBalance,
        @Schema(description = "가용 잔액") long availableBalance,
        @Schema(description = "HELD 상태인 청약금 동결 건이 하나라도 있는지 여부. true면 탈퇴 차단 판단에 참고") boolean hasActiveHold
) {
    public static WalletStatusResponse of(Wallet wallet, boolean hasActiveHold) {
        return new WalletStatusResponse(
                wallet.getId(),
                wallet.getHoldBalance(),
                wallet.getAvailableBalance(),
                hasActiveHold
        );
    }
}

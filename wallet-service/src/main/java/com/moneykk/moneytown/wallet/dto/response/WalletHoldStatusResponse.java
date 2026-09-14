package com.moneykk.moneytown.wallet.dto.response;

import com.moneykk.moneytown.wallet.entity.WalletHold;
import com.moneykk.moneytown.wallet.entity.WalletHoldStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record WalletHoldStatusResponse(
        @Schema(description = "청약 ID") UUID subscriptionId,
        @Schema(description = "동결 금액") long amount,
        @Schema(description = "동결 처리 상태") WalletHoldStatus status,
        @Schema(description = "상태 최종 변경 시각") Instant updatedAt
) {
    public static WalletHoldStatusResponse from(WalletHold hold) {
        return new WalletHoldStatusResponse(
                hold.getSubscriptionId(),
                hold.getAmount(),
                hold.getStatus(),
                hold.getUpdatedAt()
        );
    }
}

package com.moneykk.moneytown.settlement.query.dto;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record FinalSettlementPayoutListItemResponse(
        @Schema(description = "최종 정산 지급(반환) ID") UUID finalSettlementPayoutId,
        @Schema(description = "투자자(사용자) ID") UUID investorId,
        @Schema(description = "종료 시점 보유 수량") Long quantity,
        @Schema(description = "반환 금액 (보유 수량 × 단가)") Long amount,
        @Schema(description = "반환 상태") PayoutStatus status,
        @Schema(description = "재시도 횟수") Integer retryCount
) {

    public static FinalSettlementPayoutListItemResponse of(FinalSettlementPayout payout) {
        return new FinalSettlementPayoutListItemResponse(
                payout.getId(),
                payout.getInvestorId(),
                payout.getQuantity(),
                payout.getAmount(),
                payout.getStatus(),
                payout.getRetryCount()
        );
    }
}
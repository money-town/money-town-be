package com.moneykk.moneytown.settlement.query.dto;

import com.moneykk.moneytown.settlement.domain.entity.DeadLetterReason;
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
        @Schema(description = "재시도 횟수") Integer retryCount,
        @Schema(description = "DEAD_LETTER 사유. RETRY_EXCEEDED=지갑 호출 재시도 초과(재처리 가능), "
                + "RESPONSE_MISMATCH=지갑 응답의 회차 ID 불일치(재처리 불가, 지갑 트랜잭션 대조 후 관리자 수동 지급 필요). "
                + "DEAD_LETTER가 아니면 null이며, 사유 도입 이전에 DEAD_LETTER가 된 건도 null이다.",
                nullable = true) DeadLetterReason deadLetterReason,
        @Schema(description = "관리자 수동 지급이 필요한 건인지 여부 (deadLetterReason == RESPONSE_MISMATCH이고 DEAD_LETTER 상태)")
        boolean manualResolutionRequired
) {

    public static FinalSettlementPayoutListItemResponse of(FinalSettlementPayout payout) {
        return new FinalSettlementPayoutListItemResponse(
                payout.getId(),
                payout.getInvestorId(),
                payout.getQuantity(),
                payout.getAmount(),
                payout.getStatus(),
                payout.getRetryCount(),
                payout.getDeadLetterReason(),
                payout.requiresManualResolution()
        );
    }
}
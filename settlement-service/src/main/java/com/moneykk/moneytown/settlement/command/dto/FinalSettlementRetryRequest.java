package com.moneykk.moneytown.settlement.command.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record FinalSettlementRetryRequest(
        @Schema(description = "재처리할 지급 건 ID 목록. 생략하거나 null이면 회차의 DEAD_LETTER 건 전체를 재처리한다"
                + "(지갑 응답 불일치 RESPONSE_MISMATCH 건은 제외). RESPONSE_MISMATCH 건을 직접 지정하면 거절된다.")
        List<UUID> finalSettlementPayoutIds
) {
}
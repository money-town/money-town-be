package com.moneykk.moneytown.asset.dto.response;

import com.moneykk.moneytown.asset.entity.HoldingHistoryType;

import java.time.Instant;
import java.util.UUID;

/** 지분 변동 이력 항목 */
public record HoldingHistoryItemResponse(
        UUID historyId,                  // 지분 이력 ID
        UUID subscriptionId,             // 관련 청약 ID
        HoldingHistoryType historyType,  // 변동 유형
        long quantity,                   // 변동 수량
        long balanceBefore,              // 변경 전 수량
        long balanceAfter,               // 변경 후 수량
        String reason,                   // 변동 사유
        Instant createdAt                // 변동 시간
) {
}
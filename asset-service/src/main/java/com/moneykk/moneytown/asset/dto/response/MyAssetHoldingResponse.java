package com.moneykk.moneytown.asset.dto.response;

import java.time.Instant;
import java.util.UUID;

/** 특정 자산의 내 보유지분 응답 */
public record MyAssetHoldingResponse(
        UUID holdingId,      // 보유지분 ID
        UUID assetId,        // 자산 ID
        long quantity,       // 현재 보유 수량
        Instant updatedAt    // 최종 변경 시간
) {
}
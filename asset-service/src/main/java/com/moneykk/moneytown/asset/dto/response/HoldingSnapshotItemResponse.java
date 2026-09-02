package com.moneykk.moneytown.asset.dto.response;

import java.util.UUID;

/** 배당 기준일의 투자자별 보유지분 */
public record HoldingSnapshotItemResponse(

        // 보유지분 ID
        UUID holdingId,

        // 지분 보유 사용자 ID
        UUID userId,

        // 기준일 보유 수량
        long quantity
) {
}
package com.moneykk.moneytown.asset.dto.response;

import java.util.List;
import java.util.UUID;

/** 지분 변동 이력 목록 응답 */
public record HoldingHistoryListResponse(
        UUID holdingId,
        List<HoldingHistoryItemResponse> histories,
        UUID nextCursor,
        boolean hasNext
) {
}
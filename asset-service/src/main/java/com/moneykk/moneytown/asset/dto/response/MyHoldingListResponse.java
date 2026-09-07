package com.moneykk.moneytown.asset.dto.response;

import java.util.List;
import java.util.UUID;

/** 내 전체 보유지분 목록 응답 */
public record MyHoldingListResponse(

        List<MyHoldingItemResponse> items,

        UUID nextCursor,

        boolean hasNext
) {
}
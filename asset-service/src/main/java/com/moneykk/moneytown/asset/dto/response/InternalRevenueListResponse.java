package com.moneykk.moneytown.asset.dto.response;

import java.util.List;
import java.util.UUID;

/** 정산 서비스에 전달할 수익 목록 응답 */
public record InternalRevenueListResponse(
        List<RevenueDetailResponse> revenues,
        UUID nextCursor,
        boolean hasNext
) {
}
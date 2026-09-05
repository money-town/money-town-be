package com.moneykk.moneytown.asset.dto.response;

import java.util.List;
import java.util.UUID;

/** 자산별 수익 목록 응답 */
public record RevenueListResponse(
        // 조회된 수익 목록
        List<RevenueDetailResponse> revenues,

        // 다음 페이지 요청에 사용할 커서
        UUID nextCursor,

        // 다음 페이지 존재 여부
        boolean hasNext
) {
}
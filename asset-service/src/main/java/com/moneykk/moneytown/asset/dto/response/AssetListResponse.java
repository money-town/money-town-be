package com.moneykk.moneytown.asset.dto.response;

import java.util.List;
import java.util.UUID;

/** 자산 목록 조회 응답 */
public record AssetListResponse(

        // 조회된 자산 목록
        List<AssetListItemResponse> assets,

        // 다음 페이지 요청에 사용할 자산 ID
        UUID nextCursor,

        // 다음 페이지 존재 여부
        boolean hasNext
) {
}
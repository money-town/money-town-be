package com.moneykk.moneytown.asset.dto.response;

import java.util.List;
import java.util.UUID;

/** 자산 문서 목록 응답 */
public record AssetDocumentListResponse(

        List<AssetDocumentListItemResponse> documents,
        UUID nextCursor,
        boolean hasNext

) {
}
package com.moneykk.moneytown.asset.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 배당 기준일의 자산별 보유지분 조회 응답 */
public record HoldingSnapshotResponse(

        // 조회한 자산 ID
        UUID assetId,

        // 배당 기준일
        LocalDate asOf,

        // 기준일에 지분을 보유한 사용자 목록
        List<HoldingSnapshotItemResponse> holdings,

        // 다음 페이지 조회에 사용할 보유지분 ID
        UUID nextCursor,

        // 다음 페이지 존재 여부
        boolean hasNext
) {
}
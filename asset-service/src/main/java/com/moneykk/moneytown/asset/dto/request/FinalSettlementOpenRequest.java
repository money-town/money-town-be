package com.moneykk.moneytown.asset.dto.request;

import java.time.Instant;
import java.util.UUID;

/** 정산 서비스에 전달하는 최종 정산 개시 요청 */
public record FinalSettlementOpenRequest(
        UUID assetId,
        Instant terminatedAt,
        Long unitPrice
) {
}

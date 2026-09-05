package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import java.util.UUID;

/**
 * Holding 지분 회수 실패 결과.
 *
 * 보상 테이블에는 errorCode를 저장한다.
 * retryable은 재처리 가능 여부이며, 이 DTO에서 재시도를 실행하지 않는다.
 */
public record HoldingRevocationFailedPayload(
        UUID assetId,
        String errorCode,
        String errorMessage,
        Boolean retryable
) {
}
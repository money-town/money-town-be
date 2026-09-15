package com.moneykk.moneytown.offering.offering.domain.repository.projection;

import java.time.Instant;
import java.util.UUID;

/**
 * 모집 미달 처리 대상의 키셋 조회 결과입니다.
 *
 * endAt과 offeringId는 다음 배치를 조회하는 커서로 사용합니다.
 */
public record UnderSubscribedOfferingTarget(
        UUID offeringId,
        Instant endAt
) {
}
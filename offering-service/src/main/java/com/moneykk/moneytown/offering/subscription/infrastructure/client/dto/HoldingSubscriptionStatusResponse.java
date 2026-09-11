package com.moneykk.moneytown.offering.subscription.infrastructure.client.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Asset Service의 청약별 지분 처리 상태 조회 응답.
 *
 * 관리자 재처리 및 보상 과정에서
 * 해당 청약의 지분 배정·회수 처리 여부를 확인할 때 사용한다.
 */
public record HoldingSubscriptionStatusResponse(

        /**
         * 지분 처리 상태를 조회한 청약 ID.
         */
        UUID subscriptionId,

        /**
         * 해당 청약으로 생성된 Holding ID.
         *
         * 아직 지분이 배정되지 않았다면 null이다.
         */
        UUID holdingId,

        /**
         * 지분과 연결된 자산 ID.
         *
         * 처리 이력이 없다면 null일 수 있다.
         */
        UUID assetId,

        /**
         * 지분을 배정받은 투자자 ID.
         *
         * 처리 이력이 없다면 null일 수 있다.
         */
        UUID userId,

        /**
         * 해당 청약으로 배정된 지분 수량.
         */
        long allocatedQuantity,

        /**
         * 해당 청약으로 회수된 지분 수량.
         */
        long revokedQuantity,

        /**
         * 지분 배정 처리 여부.
         */
        boolean allocationProcessed,

        /**
         * 지분 회수 처리 여부.
         */
        boolean revocationProcessed,

        /**
         * 지분 배정이 차단된 상태인지 여부.
         *
         * 공모 중단 요청이 먼저 처리된 경우 true일 수 있다.
         */
        boolean allocationBlocked,

        /**
         * 해당 청약 지분 처리 상태의 마지막 변경 시각.
         *
         * 처리 이력과 차단 상태가 모두 없다면 null일 수 있다.
         */
        Instant lastProcessedAt

) {
}
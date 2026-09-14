package com.moneykk.moneytown.offering.subscription.infrastructure.client.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Wallet Service의 청약금 동결 상태 조회 응답.
 *
 * 관리자 재처리 및 보상 과정에서
 * Wallet의 실제 처리 상태를 확인할 때 사용한다.
 */
public record WalletHoldStatusResponse(

        /**
         * Wallet Hold와 연결된 청약 ID.
         */
        UUID subscriptionId,

        /**
         * 해당 청약으로 동결하거나 확정한 금액.
         */
        Long amount,

        /**
         * Wallet Hold의 현재 상태.
         *
         * HELD, RELEASED, COMMITTED, REFUNDED 중 하나다.
         */
        WalletHoldStatus status,

        /**
         * Wallet Hold 상태가 마지막으로 변경된 시각.
         */
        Instant updatedAt

) {
}
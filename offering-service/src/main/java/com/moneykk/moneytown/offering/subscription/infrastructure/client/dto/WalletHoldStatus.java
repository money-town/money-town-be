package com.moneykk.moneytown.offering.subscription.infrastructure.client.dto;

/**
 * Wallet Service가 관리하는 청약금 동결의 현재 상태.
 *
 * Offering Service는 이 상태를 조회하여
 * 관리자 재처리 또는 보상 과정에서 필요한 후속 작업을 판단한다.
 */
public enum WalletHoldStatus {

    /**
     * 청약금이 동결된 상태.
     *
     * 정상 처리에서는 HOLD를 다시 요청하지 않는다.
     * 보상 처리에서는 RELEASE가 필요하다.
     */
    HELD,

    /**
     * 동결된 청약금이 해제된 상태.
     *
     * RELEASE를 다시 요청하지 않는다.
     */
    RELEASED,

    /**
     * 청약금이 최종 확정된 상태.
     *
     * 정상 처리에서는 COMMIT을 다시 요청하지 않는다.
     * 보상이 필요하면 Wallet의 REFUND 대상이다.
     */
    COMMITTED,

    /**
     * 최종 확정된 청약금의 환불까지 완료된 상태.
     *
     * REFUND를 다시 요청하지 않는다.
     */
    REFUNDED
}
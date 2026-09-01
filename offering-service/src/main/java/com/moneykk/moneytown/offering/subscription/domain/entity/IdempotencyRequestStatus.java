package com.moneykk.moneytown.offering.subscription.domain.entity;

public enum IdempotencyRequestStatus {

    /**
     * 멱등 요청을 선점하고 실제 처리를 진행 중인 상태.
     */
    PROCESSING,

    /**
     * 요청 처리가 정상 완료된 상태.
     *
     * 동일 요청 재호출 시 기존 resourceId를 기준으로
     * 처리 결과를 다시 조회하여 반환한다.
     */
    COMPLETED,

    /**
     * 요청 처리가 실패한 상태.
     */
    FAILED
}
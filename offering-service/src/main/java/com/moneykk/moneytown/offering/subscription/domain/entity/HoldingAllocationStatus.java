package com.moneykk.moneytown.offering.subscription.domain.entity;

/**
 * 확정된 청약의 Holding 지분 배정 후처리 상태.
 *
 * PENDING: 지분 배정 결과 대기
 * SUCCEEDED: 지분 배정 완료
 * FAILED: 지분 배정 실패
 */
public enum HoldingAllocationStatus {

    PENDING,
    SUCCEEDED,
    FAILED
}
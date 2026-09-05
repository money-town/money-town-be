package com.moneykk.moneytown.offering.subscription.domain.entity;

/**
 * Wallet 보상과 Holding 회수의 진행 상태.
 * 보상 엔티티의 walletStatus, holdingStatus에서 공통으로 사용한다.
 */
public enum CompensationStatus {

    // 보상을 요청하고 결과를 기다리는 상태
    PENDING,

    // 보상 완료 또는 추가 보상이 필요 없다는 성공 결과를 확인한 상태
    SUCCEEDED,

    // 보상 실패 결과를 수신한 상태
    FAILED
}
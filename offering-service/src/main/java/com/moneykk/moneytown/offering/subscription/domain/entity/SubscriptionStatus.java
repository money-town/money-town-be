package com.moneykk.moneytown.offering.subscription.domain.entity;

public enum SubscriptionStatus {

    /**
     * 청약 접수 후 선착순 수량 확보가 완료된 상태.
     * Wallet HOLD 결과 등 후속 비동기 처리 결과를 대기한다.
     */
    PROCESSING,

    /**
     * 청약 처리가 정상적으로 완료된 상태.
     * 선착순 수량 확보와 Wallet HOLD가 모두 성공한 상태를 의미한다.
     */
    CONFIRMED,

    /**
     * 청약 처리 실패 또는 공모 중단 등의 사유로
     * 확보한 수량 및 자금에 대한 보상 처리를 진행 중인 상태.
     */
    COMPENSATING,

    /**
     * 청약이 최종적으로 거절된 상태.
     * 잔액 부족, FDS 차단, 처리 실패 등으로 청약이 성립되지 않았고
     * 필요한 보상 처리까지 완료된 상태를 의미한다.
     */
    REJECTED,

    /**
     * 청약 취소 또는 공모 중단 등의 사유로
     * 청약이 최종 취소된 상태.
     */
    CANCELLED,

    /**
     * 자동 재처리 또는 보상만으로 해결하기 어려워
     * 관리자 또는 운영자의 수동 확인이 필요한 상태.
     */
    MANUAL_REVIEW
}
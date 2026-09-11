package com.moneykk.moneytown.offering.subscription.domain.entity;

public enum IdempotencyOperation {

    /**
     * 선착순 청약 접수 요청.
     */
    CREATE_SUBSCRIPTION,

    /**
     * 관리자의 청약 보상 처리 요청.
     */
    COMPENSATE_SUBSCRIPTION
}
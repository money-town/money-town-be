package com.moneykk.moneytown.asset.entity;

/** 단가 절사 차액의 납부 방식 */
public enum OwnerBurdenPaymentMethod {
    // 매각대금에서 차액과 연 10% 이자 공제
    SALE_DEDUCTION,
    // 지갑에서 차액 납부, 이자 없음
    WALLET_PAYMENT
}

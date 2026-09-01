package com.moneykk.moneytown.wallet.entity;

public enum WalletTransactionType {
    DEPOSIT,     // 예치금 충전
    WITHDRAW,    // 예치금 출금
    HOLD,        // 청약금 동결
    UNHOLD,      // 청약금 동결 해제 (모집미달/취소로 인한 환불 전 단계)
    DEDUCT,      // 청약 확정에 따른 동결금 최종 차감
    DIVIDEND,    // 배당금 입금
    REFUND,      // 확정된 청약금 환불 (모집미달/관리자 강제취소)
    SETTLEMENT   // 자산종료 정산 원금 반환
}

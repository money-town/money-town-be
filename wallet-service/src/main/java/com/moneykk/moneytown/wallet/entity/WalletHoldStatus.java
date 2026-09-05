package com.moneykk.moneytown.wallet.entity;

public enum WalletHoldStatus {
    HELD,       // 동결 중 (생성 시 초기 상태)
    RELEASED,   // 동결 해제됨 (모집미달/관리자 강제취소)
    COMMITTED   // 동결 확정됨 (청약 성공, DEDUCT 대상)
}

package com.moneykk.moneytown.user.entity.type;

public enum KycStatus {
    NOT_SUBMITTED,  // 미신청
    PENDING,        // 심사 대기
    VERIFIED,       // 인증 승인
    REJECTED,       // 인증 거절
    EXPIRED         // 기간 만료
}

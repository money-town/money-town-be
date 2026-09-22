package com.moneykk.moneytown.settlement.domain.entity;

public enum PayoutStatus {
    QUEUED,
    PROCESSING,
    PAID,
    RETRYING,
    DEAD_LETTER,
    // DEAD_LETTER 건을 관리자가 명시적으로 포기 처리한 종결 상태. 자동으로는 절대 전이되지 않는다.
    ABANDONED
}
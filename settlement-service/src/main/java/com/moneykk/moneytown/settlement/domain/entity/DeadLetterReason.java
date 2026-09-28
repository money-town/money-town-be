package com.moneykk.moneytown.settlement.domain.entity;

// 지급 건이 DEAD_LETTER가 된 사유. 재처리 API가 "재시도로 해결될 건"과 "수동 지급이 필요한 건"을 구분하는 기준
public enum DeadLetterReason {
    // 지갑 호출이 최대 재시도 횟수만큼 실패 — 재시도 대상
    RETRY_EXCEEDED,
    // 지갑이 success=true로 응답했지만 응답의 회차 ID가 요청과 다름 — 지갑에서 실제로 어떻게 처리됐는지 알 수 없어
    // 재시도 대상이 아니며, 관리자가 지갑 트랜잭션을 대조한 뒤 수동 지급(abandon)해야 한다
    RESPONSE_MISMATCH
}
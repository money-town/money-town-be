package com.moneykk.moneytown.settlement.domain.entity;

// 관리자가 DEAD_LETTER 지급 건을 자동 재시도 대상에서 뺄 때(ABANDONED),
// 실제로는 어떤 방법으로 투자자에게 지급을 완료했는지를 기록
public enum ResolutionType {
    // 관리자가 플랫폼 밖에서 은행 계좌로 직접 송금 — 증빙(resolutionReference)은 형식 검증만 가능(은행 연동 없음)
    BANK_TRANSFER,
    // 은행 송금으로 분류 안 되는 경우 — resolutionNote(상세 설명) 필수
    OTHER
}
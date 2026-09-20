package com.moneykk.moneytown.settlement.domain.entity;

// 관리자가 DEAD_LETTER 지급 건을 자동 재시도 대상에서 뺄 때(ABANDONED),
// 실제로는 어떤 방법으로 투자자에게 지급을 완료했는지를 기록
public enum ResolutionType {
    // 관리자가 플랫폼 밖에서 은행 계좌로 직접 송금 — 증빙(resolutionReference)은 형식 검증만 가능(은행 연동 없음)
    BANK_TRANSFER,
    // 투자자 지갑을 복구한 뒤 지갑으로 재입금 — 증빙은 wallet-service의 실제 거래 ID(향후 진위 검증 가능, 이번엔 문자열 저장만)
    WALLET_REDEPOSIT,
    // 위 두 가지로 분류 안 되는 경우 — resolutionNote(상세 설명) 필수
    OTHER
}
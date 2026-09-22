package com.moneykk.moneytown.settlement.domain.entity;

public enum SettlementStatus {
    PENDING,
    SNAPSHOT_TAKEN,
    CALCULATED,
    DISBURSING,
    COMPLETED,
    PARTIAL_FAILED,
    FAILED,
    // 남은 DEAD_LETTER 건을 관리자가 전부 ABANDONED 처리해 마감한 종결 상태
    CLOSED_ABANDONED
}
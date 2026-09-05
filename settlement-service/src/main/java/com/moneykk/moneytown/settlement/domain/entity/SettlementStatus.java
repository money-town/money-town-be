package com.moneykk.moneytown.settlement.domain.entity;

public enum SettlementStatus {
    PENDING,
    SNAPSHOT_TAKEN,
    CALCULATED,
    DISBURSING,
    COMPLETED,
    PARTIAL_FAILED,
    FAILED
}
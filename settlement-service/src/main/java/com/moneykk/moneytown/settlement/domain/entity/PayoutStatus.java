package com.moneykk.moneytown.settlement.domain.entity;

public enum PayoutStatus {
    QUEUED,
    PROCESSING,
    PAID,
    RETRYING,
    DEAD_LETTER
}
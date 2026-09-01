package com.moneykk.moneytown.analysis.fds.domain;

public enum EventType {
    SUBSCRIPTION_REQUEST,    // 청약 요청 (Pre-FDS 진입점)
    SUBSCRIPTION_SUCCESS,    // 청약 성공
    SUBSCRIPTION_FAILED,     // 청약 실패 (한도 초과 포함)
    SUBSCRIPTION_CANCELLED   // 청약 취소
}

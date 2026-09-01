package com.moneykk.moneytown.offering.offering.domain.entity;

public enum CancellationType {

    // 관리자가 공모를 강제로 중단
    ADMIN_CANCELLED,

    // 모집 수량 미달로 공모 무효
    UNDER_SUBSCRIBED
}
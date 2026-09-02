package com.moneykk.moneytown.offering.subscription.domain.entity;

public enum CancellationType {

    // 운영자가 공모를 중단함에 따라 해당 공모에 연결된 청약이 취소된 경우
    OFFERING_ADMIN_CANCELLED,

    // 최소 모집 기준 미달 등 공모 조건 미충족으로 공모가 정상 확정되지 않아 청약이 취소된 경우
    OFFERING_UNDER_SUBSCRIBED
}
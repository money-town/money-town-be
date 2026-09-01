package com.moneykk.moneytown.offering.offering.domain.entity;

public enum OfferingStatus {

    // 공모 작성 중
    DRAFT,

    // 발행자가 심사를 요청한 상태
    REVIEW_REQUESTED,

    // 관리자 승인 완료, 모집 시작 대기
    SCHEDULED,

    // 모집 진행 중
    OPEN,

    // 모집 수량 전량 소진
    SOLD_OUT,

    // 정상 모집 종료
    CLOSED,

    // 관리자 심사 반려
    REJECTED,

    // 공모 취소에 따른 보상 처리 진행 중
    CANCELLING,

    // 공모 취소 및 보상 처리 완료
    CANCELLED
}
package com.moneykk.moneytown.offering.global.outbox;

public enum OutboxEventStatus {

    // 최초 발행 또는 재시도 대기
    PENDING,

    // 발행 작업이 선점되어 현재 처리 중
    PROCESSING,

    // Kafka 발행 성공 확인
    PUBLISHED,

    // 재시도 한도 초과 등으로 자동 발행 중단
    FAILED
}
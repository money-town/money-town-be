package com.moneykk.moneytown.analysis.fds.domain;

import java.util.Optional;

public enum EventType {
    SUBSCRIPTION_REQUEST,    // 청약 요청 (Pre-FDS 진입점)
    SUBSCRIPTION_SUCCESS,    // 청약 성공
    SUBSCRIPTION_FAILED,     // 청약 실패 (한도 초과 제외)
    SUBSCRIPTION_CANCELLED,  // 청약 취소
    SUBSCRIPTION_LIMIT_EXCEEDED;

    public static Optional<EventType> fromEventName(String eventName){
        if(eventName == null || eventName.isBlank()) return Optional.empty();
        return  switch (eventName){
            case "SubscriptionFailed",         "SUBSCRIPTION_FAILED"          ->
                    Optional.of(SUBSCRIPTION_FAILED);
            case "SubscriptionLimitExceeded",  "SUBSCRIPTION_LIMIT_EXCEEDED"  -> Optional.of(SUBSCRIPTION_LIMIT_EXCEEDED);
            case "SubscriptionCancelled",      "SUBSCRIPTION_CANCELLED"       -> Optional.of(SUBSCRIPTION_CANCELLED);
            case "SubscriptionSuccess",        "SUBSCRIPTION_SUCCESS"         -> Optional.of(SUBSCRIPTION_SUCCESS);
            case "SubscriptionRequest",        "SUBSCRIPTION_REQUEST"         -> Optional.of(SUBSCRIPTION_REQUEST);
            default -> Optional.empty();
        };
    }

}

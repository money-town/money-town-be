package com.moneykk.moneytown.wallet.consumer.dto;

import java.util.UUID;

// User 서비스가 실제로 발행하는 flat 구조 그대로
public record UserRegisteredEvent(
        UUID eventId,
        String eventType,
        UUID userId
) {
}

package com.moneykk.moneytown.wallet.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// SubscriptionCompensationRequestedConsumer가 받는 EventEnvelope의 payload (Offering → Wallet, 보상 트리거)
// Holding용 필드도 같이 오지만 Wallet은 reason만 사용.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubscriptionCompensationRequestedPayload(String reason) {
}

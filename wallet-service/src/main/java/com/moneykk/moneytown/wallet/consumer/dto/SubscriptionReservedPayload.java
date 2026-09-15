package com.moneykk.moneytown.wallet.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// SubscriptionReservedConsumer가 받는 EventEnvelope의 payload (Offering → Wallet, 청약 예약)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubscriptionReservedPayload(long amount) {
}

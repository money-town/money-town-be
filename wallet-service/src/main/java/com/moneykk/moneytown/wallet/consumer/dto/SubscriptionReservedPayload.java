package com.moneykk.moneytown.wallet.consumer.dto;

// SubscriptionReservedConsumer가 받는 EventEnvelope의 payload (Offering → Wallet, 청약 예약)
public record SubscriptionReservedPayload(long amount) {
}

package com.moneykk.moneytown.wallet.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

// DividendPayoutDispatchConsumer가 받는 EventEnvelope의 payload (Settlement → Wallet, 배당 지급 요청)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DividendPayoutDispatchPayload(UUID payoutId, UUID settlementBatchId, UUID investorId, Long amount) {
}

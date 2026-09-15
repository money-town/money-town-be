package com.moneykk.moneytown.wallet.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

// UserWithdrawnConsumer가 받는 EventEnvelope의 payload (User → Wallet, 회원 탈퇴 전파)
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserWithdrawnPayload(UUID withdrawnBy) {
}

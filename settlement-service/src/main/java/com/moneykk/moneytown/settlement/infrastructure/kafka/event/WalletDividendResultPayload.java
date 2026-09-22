package com.moneykk.moneytown.settlement.infrastructure.kafka.event;

import java.util.UUID;

// wallet-service가 발행하는 배당 지급 결과 payload
// Succeeded/Failed 둘 다 이 레코드 하나로 표현하고 EventEnvelope.eventType으로 구분한다.
// 메시지 key는 investorId(EventEnvelope.userId)라 payoutId(aggregateId)와는 무관하게 분산
public record WalletDividendResultPayload(
        UUID payoutId,
        UUID settlementBatchId,
        Long walletId,
        Long transactionId,
        String reason
) {

    public static final String TOPIC = "wallet-dividend-result";
    public static final String DLT_TOPIC = TOPIC + "-dlt";
    public static final String SUCCEEDED_EVENT_TYPE = "DividendPayoutDispatchSucceeded";
    public static final String FAILED_EVENT_TYPE = "DividendPayoutDispatchFailed";
    public static final int PARTITIONS = 4;
}
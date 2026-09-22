package com.moneykk.moneytown.settlement.infrastructure.kafka.event;

import java.util.UUID;

// payout 1건당 메시지 1개. 메시지 key는 payoutId(EventEnvelope.aggregateId)라
// 한 회차의 payout이 파티션 전체에 분산된다. assetId는 key로 쓰지 않으므로 payload에 넣지 않는다.
public record DividendPayoutDispatchPayload(
        UUID payoutId,
        UUID settlementBatchId,
        UUID investorId,
        Long amount
) {

    public static final String TOPIC = "dividend-payout-dispatch-requested";
    public static final String DLT_TOPIC = TOPIC + ".DLT";
    public static final String EVENT_TYPE = "DividendPayoutDispatchRequested";
    public static final String AGGREGATE_TYPE = "SETTLEMENT";
    public static final int PARTITIONS = 8;
}
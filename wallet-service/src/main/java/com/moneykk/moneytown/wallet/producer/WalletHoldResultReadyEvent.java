package com.moneykk.moneytown.wallet.producer;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.producer.dto.WalletHoldResultPayload;

// 커밋 후 발행을 위해 실제 Kafka 발행 전에 큐잉하는 이벤트
public record WalletHoldResultReadyEvent(EventEnvelope<WalletHoldResultPayload> event) {
}

package com.moneykk.moneytown.wallet.producer;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.producer.dto.WalletCompensationResultPayload;

// 커밋 후 발행을 위해 큐잉하는 이벤트 (WalletHoldResultReadyEvent와 동일한 목적)
public record WalletCompensationResultReadyEvent(EventEnvelope<WalletCompensationResultPayload> event) {
}

package com.moneykk.moneytown.wallet.consumer;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.consumer.dto.DividendPayoutDispatchPayload;
import com.moneykk.moneytown.wallet.service.WalletDividendDispatchService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 배당 지급 요청 이벤트 수신 → 지갑 입금 처리 (Settlement → Wallet)
@Component
@RequiredArgsConstructor
public class DividendPayoutDispatchConsumer {

    private final WalletDividendDispatchService walletDividendDispatchService;

    @KafkaListener(
            topics = "dividend-payout-dispatch-requested",
            groupId = "${spring.application.name}",
            containerFactory = "dividendPayoutDispatchKafkaListenerContainerFactory"
    )
    public void onDividendPayoutDispatchRequested(EventEnvelope<DividendPayoutDispatchPayload> event) {
        try {
            MDC.put("requestId", event.correlationId());
            walletDividendDispatchService.processDividendDispatch(event);
        } finally {
            MDC.remove("requestId");
        }
    }
}

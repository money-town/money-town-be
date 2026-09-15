package com.moneykk.moneytown.wallet.producer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// WalletHoldService가 큐잉만 해둔 결과 이벤트를, 트랜잭션이 실제로 커밋된 뒤에만 Kafka로 발행한다.
@Component
@RequiredArgsConstructor
public class WalletEventPublishingListener {

    private final WalletEventPublisher walletEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHoldResultReady(WalletHoldResultReadyEvent event) {
        walletEventPublisher.publishHoldResult(event.event());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompensationResultReady(WalletCompensationResultReadyEvent event) {
        walletEventPublisher.publishCompensationResult(event.event());
    }
}

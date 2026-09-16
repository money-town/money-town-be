package com.moneykk.moneytown.wallet.producer;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.producer.dto.WalletCompensationResultPayload;
import com.moneykk.moneytown.wallet.producer.dto.WalletHoldResultPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WalletEventPublishingListenerTest {

    @Mock
    private WalletEventPublisher walletEventPublisher;

    @InjectMocks
    private WalletEventPublishingListener listener;

    @Test
    @DisplayName("커밋 후 큐잉된 Hold 결과 이벤트를 실제로 발행한다")
    void onHoldResultReady_publishesHoldResult() {
        EventEnvelope<WalletHoldResultPayload> event = WalletHoldResultPayload.succeeded(
                UUID.randomUUID().toString(), UUID.randomUUID(), "corr-1", 1L, 1L);

        listener.onHoldResultReady(new WalletHoldResultReadyEvent(event));

        verify(walletEventPublisher).publishHoldResult(event);
    }

    @Test
    @DisplayName("커밋 후 큐잉된 보상 결과 이벤트를 실제로 발행한다")
    void onCompensationResultReady_publishesCompensationResult() {
        EventEnvelope<WalletCompensationResultPayload> event = WalletCompensationResultPayload.succeeded(
                UUID.randomUUID().toString(), UUID.randomUUID(), "corr-1", 1L, 1L, "RELEASE", 1L, 1_000L);

        listener.onCompensationResultReady(new WalletCompensationResultReadyEvent(event));

        verify(walletEventPublisher).publishCompensationResult(event);
    }
}

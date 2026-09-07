package com.moneykk.moneytown.wallet.producer;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.producer.dto.WalletHoldResultPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private WalletEventPublisher walletEventPublisher;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("send()가 동기적으로 예외를 던지면 로그 남기고 그대로 다시 던진다")
    void publishHoldResult_synchronousSendFailure_rethrows() {
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenThrow(new KafkaException("producer buffer full"));

        assertThrows(KafkaException.class, () -> walletEventPublisher.publishHoldResult(holdEvent()));
    }

    @Test
    @DisplayName("send()가 반환한 Future가 비동기로 실패해도 호출자에게 예외를 던지지 않는다 (로그로만 처리)")
    void publishHoldResult_asyncSendFailure_doesNotThrow() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("broker timeout"));
        when(kafkaTemplate.send(any(String.class), any(String.class), any())).thenReturn(future);

        assertDoesNotThrow(() -> walletEventPublisher.publishHoldResult(holdEvent()));
    }

    private EventEnvelope<WalletHoldResultPayload> holdEvent() {
        return WalletHoldResultPayload.succeeded(UUID.randomUUID().toString(), userId, "corr-1", 1L, 1L);
    }
}

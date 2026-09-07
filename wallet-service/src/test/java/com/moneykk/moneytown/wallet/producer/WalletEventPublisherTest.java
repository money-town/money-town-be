package com.moneykk.moneytown.wallet.producer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.producer.dto.WalletHoldResultPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletEventPublisherTest {

    private static final String WALLET_HOLD_RESULT_TOPIC = "wallet-hold-result";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private WalletEventPublisher walletEventPublisher;

    private final UUID userId = UUID.randomUUID();

    private Logger publisherLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUpLogCapture() {
        publisherLogger = (Logger) LoggerFactory.getLogger(WalletEventPublisher.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        publisherLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDownLogCapture() {
        publisherLogger.detachAppender(logAppender);
    }

    @Test
    @DisplayName("send()가 동기적으로 예외를 던지면 로그 남기고 그대로 다시 던진다")
    void publishHoldResult_synchronousSendFailure_rethrows() {
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenThrow(new KafkaException("producer buffer full"));

        assertThrows(KafkaException.class, () -> walletEventPublisher.publishHoldResult(holdEvent()));
        assertFailureLogged();
    }

    @Test
    @DisplayName("send()가 반환한 Future가 비동기로 실패하면 실패 로그를 남기고 예외는 던지지 않는다")
    void publishHoldResult_asyncSendFailure_logsAndDoesNotThrow() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(String.class), any(String.class), any())).thenReturn(future);

        walletEventPublisher.publishHoldResult(holdEvent());
        // 발행 호출이 끝난 뒤에 비동기로 실패시켜야 whenComplete 콜백이 실제로 검증됨
        future.completeExceptionally(new RuntimeException("broker timeout"));

        assertFailureLogged();
    }

    private void assertFailureLogged() {
        assertEquals(1, logAppender.list.size());
        ILoggingEvent logEvent = logAppender.list.get(0);
        assertEquals(Level.ERROR, logEvent.getLevel());
        assertTrue(logEvent.getFormattedMessage().contains(WALLET_HOLD_RESULT_TOPIC));
    }

    private EventEnvelope<WalletHoldResultPayload> holdEvent() {
        return WalletHoldResultPayload.succeeded(UUID.randomUUID().toString(), userId, "corr-1", 1L, 1L);
    }
}

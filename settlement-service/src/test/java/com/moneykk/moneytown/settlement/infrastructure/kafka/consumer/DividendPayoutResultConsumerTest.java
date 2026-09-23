package com.moneykk.moneytown.settlement.infrastructure.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.infrastructure.kafka.event.WalletDividendResultPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DividendPayoutResultConsumerTest {

    @Mock
    private DividendDisbursementService dividendDisbursementService;

    private ObjectMapper objectMapper;
    private DividendPayoutResultConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new DividendPayoutResultConsumer(objectMapper, dividendDisbursementService);
    }

    @Test
    @DisplayName("성공 결과 메시지를 succeeded=true로 위임한다")
    void delegatesSucceededResult() throws Exception {
        UUID payoutId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        consumer.consume(message(WalletDividendResultPayload.SUCCEEDED_EVENT_TYPE, payoutId, batchId, null));

        verify(dividendDisbursementService).applyDispatchResult(payoutId, batchId, true, null);
    }

    @Test
    @DisplayName("실패 결과 메시지를 succeeded=false와 reason으로 위임한다")
    void delegatesFailedResult() throws Exception {
        UUID payoutId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        consumer.consume(message(WalletDividendResultPayload.FAILED_EVENT_TYPE, payoutId, batchId, "WALLET_NOT_FOUND"));

        verify(dividendDisbursementService).applyDispatchResult(payoutId, batchId, false, "WALLET_NOT_FOUND");
    }

    @Test
    @DisplayName("지원하지 않는 이벤트 타입이면 예외를 던지고(→ Kafka 재시도·DLT) 결과 반영은 하지 않는다")
    void rejectsUnsupportedEventType() throws Exception {
        String message = message("SomethingElse", UUID.randomUUID(), UUID.randomUUID(), null);

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(IllegalArgumentException.class);

        verify(dividendDisbursementService, never()).applyDispatchResult(any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("역직렬화할 수 없는 메시지는 예외를 던진다")
    void failsOnMalformedMessage() {
        assertThatThrownBy(() -> consumer.consume("not-json"))
                .isInstanceOf(JsonProcessingException.class);
    }

    private String message(String eventType, UUID payoutId, UUID batchId, String reason) throws JsonProcessingException {
        EventEnvelope<WalletDividendResultPayload> event = EventEnvelope.of(
                eventType, payoutId.toString(), UUID.randomUUID(), batchId.toString(),
                new WalletDividendResultPayload(payoutId, batchId, 1L, reason == null ? 1L : null, reason));
        return objectMapper.writeValueAsString(event);
    }
}
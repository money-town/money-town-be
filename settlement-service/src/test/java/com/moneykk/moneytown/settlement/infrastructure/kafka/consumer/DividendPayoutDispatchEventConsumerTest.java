package com.moneykk.moneytown.settlement.infrastructure.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.infrastructure.kafka.event.DividendPayoutDispatchPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DividendPayoutDispatchEventConsumerTest {

    @Mock
    private DividendDisbursementService dividendDisbursementService;

    private ObjectMapper objectMapper;
    private DividendPayoutDispatchEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new DividendPayoutDispatchEventConsumer(objectMapper, dividendDisbursementService);
    }

    @Test
    @DisplayName("지급 요청 메시지를 payout 1건 처리로 위임한다")
    void delegatesPayoutToDisbursementService() throws Exception {
        UUID payoutId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        UUID investorId = UUID.randomUUID();

        consumer.consume(message(DividendPayoutDispatchPayload.EVENT_TYPE, payoutId, batchId, investorId, 10_000L));

        verify(dividendDisbursementService).processDispatchedPayout(payoutId, batchId, investorId, 10_000L);
    }

    @Test
    @DisplayName("지원하지 않는 이벤트 타입이면 예외를 던지고(→ Kafka 재시도·DLT) 지급 처리는 하지 않는다")
    void rejectsUnsupportedEventType() throws Exception {
        String message = message("SomethingElse", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 10_000L);

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(IllegalArgumentException.class);

        verify(dividendDisbursementService, never()).processDispatchedPayout(any(), any(), any(), any());
    }

    @Test
    @DisplayName("역직렬화할 수 없는 메시지는 예외를 던진다")
    void failsOnMalformedMessage() {
        assertThatThrownBy(() -> consumer.consume("not-json"))
                .isInstanceOf(JsonProcessingException.class);
    }

    private String message(String eventType, UUID payoutId, UUID batchId, UUID investorId, Long amount)
            throws JsonProcessingException {
        EventEnvelope<DividendPayoutDispatchPayload> event = EventEnvelope.of(
                eventType, payoutId.toString(), null, batchId.toString(),
                new DividendPayoutDispatchPayload(payoutId, batchId, investorId, amount));
        return objectMapper.writeValueAsString(event);
    }
}
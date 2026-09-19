package com.moneykk.moneytown.offering.global.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OutboxKafkaPublisherTest {

    private static final String TOPIC = "subscription-confirmed";
    private static final String MESSAGE_KEY =
            "001b9d29-a775-9ebe-e28c-8ed7e87601d1";
    private static final String ENVELOPE_JSON =
            """
            {
              "eventId": "7b0e8f8a-9f05-408d-be6e-770ad9279d17",
              "eventType": "SubscriptionConfirmed",
              "aggregateId": "b251923-2863-4c0c-9794-a37131c69096"
            }
            """;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxKafkaPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxKafkaPublisher(kafkaTemplate);
    }

    @Test
    @DisplayName("Outbox 이벤트의 topic, key, payload를 KafkaTemplate에 전달한다")
    void publishesEventWithTopicKeyAndPayload() {
        OutboxPublishService.ClaimedEvent event = claimedEvent();

        CompletableFuture<SendResult<String, String>> sendFuture =
                new CompletableFuture<>();

        given(kafkaTemplate.send(
                TOPIC,
                MESSAGE_KEY,
                ENVELOPE_JSON
        )).willReturn(sendFuture);

        CompletableFuture<SendResult<String, String>> result =
                publisher.publish(event, MESSAGE_KEY);

        assertThat(result).isSameAs(sendFuture);

        verify(kafkaTemplate).send(
                TOPIC,
                MESSAGE_KEY,
                ENVELOPE_JSON
        );
    }

    @Test
    @DisplayName("발행할 Outbox 이벤트가 null이면 거부한다")
    void rejectsNullEvent() {
        assertThatThrownBy(() ->
                publisher.publish(null, MESSAGE_KEY)
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("발행할 이벤트는 필수입니다.");

        verifyNoInteractions(kafkaTemplate);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            " ",
            "   ",
            "\t"
    })
    @DisplayName("Kafka 메시지 key가 비어 있으면 거부한다")
    void rejectsBlankMessageKey(String messageKey) {
        OutboxPublishService.ClaimedEvent event = claimedEvent();

        assertThatThrownBy(() ->
                publisher.publish(event, messageKey)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kafka 메시지 key는 필수입니다.");

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("KafkaTemplate이 동기적으로 예외를 발생시키면 그대로 전파한다")
    void propagatesSynchronousKafkaFailure() {
        OutboxPublishService.ClaimedEvent event = claimedEvent();
        RuntimeException failure =
                new RuntimeException("Kafka producer 호출 실패");

        given(kafkaTemplate.send(
                TOPIC,
                MESSAGE_KEY,
                ENVELOPE_JSON
        )).willThrow(failure);

        assertThatThrownBy(() ->
                publisher.publish(event, MESSAGE_KEY)
        ).isSameAs(failure);

        verify(kafkaTemplate).send(
                TOPIC,
                MESSAGE_KEY,
                ENVELOPE_JSON
        );
    }

    private OutboxPublishService.ClaimedEvent claimedEvent() {
        return new OutboxPublishService.ClaimedEvent(
                UUID.randomUUID(),
                TOPIC,
                ENVELOPE_JSON,
                Instant.parse("2026-09-17T01:00:00Z")
        );
    }
}
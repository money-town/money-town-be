package com.moneykk.moneytown.wallet.global.config;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionReservedPayload;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// 역직렬화 실패 시 DLQ로 보내지는지(§KafkaConsumerErrorHandler)는 기존 테스트가 커버하지만,
// 그 앞단인 "envelopeConsumerFactory가 실제로 만드는 Deserializer가 올바르게 동작하는지"는 검증이 없었다.
class KafkaConsumerFactoryConfigTest {

    private final KafkaConsumerConfig config = new KafkaConsumerConfig();
    private final KafkaProperties kafkaProperties = new KafkaProperties();

    @Test
    @DisplayName("정상 JSON은 EventEnvelope<SubscriptionReservedPayload>로 역직렬화된다")
    void envelopeConsumerFactory_validJson_deserializesToTypedPayload() {
        ConsumerFactory<String, EventEnvelope<SubscriptionReservedPayload>> factory =
                config.subscriptionReservedConsumerFactory(kafkaProperties);
        Deserializer<EventEnvelope<SubscriptionReservedPayload>> deserializer = factory.getValueDeserializer();

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String json = """
                {"eventId":"%s","eventType":"SubscriptionReserved","aggregateId":"agg-1",
                "userId":"%s","occurredAt":"%s","correlationId":"corr-1","payload":{"amount":10000}}
                """.formatted(eventId, userId, Instant.parse("2026-09-10T09:00:00Z"));

        EventEnvelope<SubscriptionReservedPayload> result = deserializer.deserialize(
                "subscription-reserved", new RecordHeaders(), json.getBytes(StandardCharsets.UTF_8));

        assertThat(result).isNotNull();
        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.payload().amount()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("깨진 JSON은 예외를 던지지 않고 헤더에 실패 정보만 담아 null을 반환한다 (DLQ 라우팅을 위해)")
    void envelopeConsumerFactory_malformedJson_doesNotThrowAndTagsHeader() {
        ConsumerFactory<String, EventEnvelope<SubscriptionReservedPayload>> factory =
                config.subscriptionReservedConsumerFactory(kafkaProperties);
        Deserializer<EventEnvelope<SubscriptionReservedPayload>> deserializer = factory.getValueDeserializer();

        RecordHeaders headers = new RecordHeaders();
        EventEnvelope<SubscriptionReservedPayload> result = deserializer.deserialize(
                "subscription-reserved", headers, "{not-valid-json".getBytes(StandardCharsets.UTF_8));

        assertThat(result).isNull();
        DeserializationException exception = SerializationUtils.byteArrayToDeserializationException(
                new LogAccessor(getClass()),
                headers.lastHeader(SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER));
        assertThat(exception).isNotNull();
    }

    @Test
    @DisplayName("컨슈머별 컨테이너 팩토리는 지정한 컨슈머 팩토리와 에러 핸들러를 그대로 사용한다")
    void kafkaListenerContainerFactory_usesGivenConsumerFactoryAndErrorHandler() {
        ConsumerFactory<String, EventEnvelope<SubscriptionReservedPayload>> consumerFactory =
                config.subscriptionReservedConsumerFactory(kafkaProperties);
        CommonErrorHandler errorHandler = config.kafkaConsumerErrorHandler(mock(KafkaOperations.class));

        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<SubscriptionReservedPayload>> containerFactory =
                config.subscriptionReservedKafkaListenerContainerFactory(consumerFactory, errorHandler);

        assertThat(containerFactory.getConsumerFactory()).isSameAs(consumerFactory);
    }
}

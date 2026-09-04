package com.moneykk.moneytown.wallet.global.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionCompensationRequestedPayload;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionReservedPayload;
import com.moneykk.moneytown.wallet.consumer.dto.UserRegisteredEvent;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

// UserRegistered는 User 서비스가 자체 flat 구조로 발행하지만, Offering이 발행하는 나머지 셋은
// 공용 EventEnvelope<T>로 감싸져 오므로 payload 타입별로 JavaType을 만들어 바인딩한다.
@Configuration
public class KafkaConsumerConfig {

    private static final String CONSUMER_DTO_PACKAGE = "com.moneykk.moneytown.wallet.consumer.dto";
    private static final String COMMON_EVENT_PACKAGE = "com.moneykk.moneytown.common.event";

    @Bean
    public ConsumerFactory<String, UserRegisteredEvent> userRegisteredConsumerFactory(KafkaProperties kafkaProperties) {
        return consumerFactory(kafkaProperties, UserRegisteredEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserRegisteredEvent> userRegisteredKafkaListenerContainerFactory(
            ConsumerFactory<String, UserRegisteredEvent> userRegisteredConsumerFactory
    ) {
        return containerFactory(userRegisteredConsumerFactory);
    }

    @Bean
    public ConsumerFactory<String, EventEnvelope<SubscriptionReservedPayload>> subscriptionReservedConsumerFactory(
            KafkaProperties kafkaProperties
    ) {
        return envelopeConsumerFactory(kafkaProperties, SubscriptionReservedPayload.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<SubscriptionReservedPayload>> subscriptionReservedKafkaListenerContainerFactory(
            ConsumerFactory<String, EventEnvelope<SubscriptionReservedPayload>> subscriptionReservedConsumerFactory
    ) {
        return containerFactory(subscriptionReservedConsumerFactory);
    }

    @Bean
    public ConsumerFactory<String, EventEnvelope<Object>> subscriptionConfirmedConsumerFactory(KafkaProperties kafkaProperties) {
        return envelopeConsumerFactory(kafkaProperties, Object.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<Object>> subscriptionConfirmedKafkaListenerContainerFactory(
            ConsumerFactory<String, EventEnvelope<Object>> subscriptionConfirmedConsumerFactory
    ) {
        return containerFactory(subscriptionConfirmedConsumerFactory);
    }

    @Bean
    public ConsumerFactory<String, EventEnvelope<SubscriptionCompensationRequestedPayload>> subscriptionCompensationRequestedConsumerFactory(
            KafkaProperties kafkaProperties
    ) {
        return envelopeConsumerFactory(kafkaProperties, SubscriptionCompensationRequestedPayload.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<SubscriptionCompensationRequestedPayload>> subscriptionCompensationRequestedKafkaListenerContainerFactory(
            ConsumerFactory<String, EventEnvelope<SubscriptionCompensationRequestedPayload>> subscriptionCompensationRequestedConsumerFactory
    ) {
        return containerFactory(subscriptionCompensationRequestedConsumerFactory);
    }

    private <T> ConsumerFactory<String, T> consumerFactory(KafkaProperties kafkaProperties, Class<T> type) {
        JsonDeserializer<T> deserializer = new JsonDeserializer<>(type);
        deserializer.addTrustedPackages(CONSUMER_DTO_PACKAGE);
        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(null),
                new StringDeserializer(),
                deserializer
        );
    }

    private <T> ConsumerFactory<String, EventEnvelope<T>> envelopeConsumerFactory(KafkaProperties kafkaProperties, Class<T> payloadType) {
        JavaType javaType = TypeFactory.defaultInstance().constructParametricType(EventEnvelope.class, payloadType);
        JsonDeserializer<EventEnvelope<T>> deserializer = new JsonDeserializer<>(javaType);
        deserializer.addTrustedPackages(CONSUMER_DTO_PACKAGE, COMMON_EVENT_PACKAGE);
        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(null),
                new StringDeserializer(),
                deserializer
        );
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> containerFactory(ConsumerFactory<String, T> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        return factory;
    }
}

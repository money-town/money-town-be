package com.moneykk.moneytown.wallet.global.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionCompensationRequestedPayload;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionReservedPayload;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

// 이벤트는 전부 EventEnvelope<T>로 오므로 payload 타입별 JavaType으로 바인딩한다.
@Configuration
public class KafkaConsumerConfig {

    private static final String CONSUMER_DTO_PACKAGE = "com.moneykk.moneytown.wallet.consumer.dto";
    private static final String COMMON_EVENT_PACKAGE = "com.moneykk.moneytown.common.event";

    @Bean
    public ConsumerFactory<String, EventEnvelope<Object>> userAccountEventConsumerFactory(KafkaProperties kafkaProperties) {
        return envelopeConsumerFactory(kafkaProperties, Object.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<Object>> userAccountEventKafkaListenerContainerFactory(
            ConsumerFactory<String, EventEnvelope<Object>> userAccountEventConsumerFactory
    ) {
        return containerFactory(userAccountEventConsumerFactory);
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

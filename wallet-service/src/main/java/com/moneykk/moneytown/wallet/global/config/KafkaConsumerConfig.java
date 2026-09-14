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
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
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
            ConsumerFactory<String, EventEnvelope<Object>> userAccountEventConsumerFactory,
            CommonErrorHandler kafkaConsumerErrorHandler
    ) {
        return containerFactory(userAccountEventConsumerFactory, kafkaConsumerErrorHandler);
    }

    @Bean
    public ConsumerFactory<String, EventEnvelope<SubscriptionReservedPayload>> subscriptionReservedConsumerFactory(
            KafkaProperties kafkaProperties
    ) {
        return envelopeConsumerFactory(kafkaProperties, SubscriptionReservedPayload.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<SubscriptionReservedPayload>> subscriptionReservedKafkaListenerContainerFactory(
            ConsumerFactory<String, EventEnvelope<SubscriptionReservedPayload>> subscriptionReservedConsumerFactory,
            CommonErrorHandler kafkaConsumerErrorHandler
    ) {
        return containerFactory(subscriptionReservedConsumerFactory, kafkaConsumerErrorHandler);
    }

    @Bean
    public ConsumerFactory<String, EventEnvelope<Object>> subscriptionConfirmedConsumerFactory(KafkaProperties kafkaProperties) {
        return envelopeConsumerFactory(kafkaProperties, Object.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<Object>> subscriptionConfirmedKafkaListenerContainerFactory(
            ConsumerFactory<String, EventEnvelope<Object>> subscriptionConfirmedConsumerFactory,
            CommonErrorHandler kafkaConsumerErrorHandler
    ) {
        return containerFactory(subscriptionConfirmedConsumerFactory, kafkaConsumerErrorHandler);
    }

    @Bean
    public ConsumerFactory<String, EventEnvelope<SubscriptionCompensationRequestedPayload>> subscriptionCompensationRequestedConsumerFactory(
            KafkaProperties kafkaProperties
    ) {
        return envelopeConsumerFactory(kafkaProperties, SubscriptionCompensationRequestedPayload.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<SubscriptionCompensationRequestedPayload>> subscriptionCompensationRequestedKafkaListenerContainerFactory(
            ConsumerFactory<String, EventEnvelope<SubscriptionCompensationRequestedPayload>> subscriptionCompensationRequestedConsumerFactory,
            CommonErrorHandler kafkaConsumerErrorHandler
    ) {
        return containerFactory(subscriptionCompensationRequestedConsumerFactory, kafkaConsumerErrorHandler);
    }

    // 1초→2초→4초 간격 3회 재시도, 그래도 실패하면 "{원본토픽}-dlt"로 보내고 다음 메시지로 넘어간다.
    @Bean
    public CommonErrorHandler kafkaConsumerErrorHandler(KafkaOperations<Object, Object> kafkaOperations) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations);

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);

        return new DefaultErrorHandler(recoverer, backOff);
    }

    private <T> ConsumerFactory<String, EventEnvelope<T>> envelopeConsumerFactory(KafkaProperties kafkaProperties, Class<T> payloadType) {
        JavaType javaType = TypeFactory.defaultInstance().constructParametricType(EventEnvelope.class, payloadType);
        JsonDeserializer<EventEnvelope<T>> jsonDeserializer = new JsonDeserializer<>(javaType);
        jsonDeserializer.addTrustedPackages(CONSUMER_DTO_PACKAGE, COMMON_EVENT_PACKAGE);
        jsonDeserializer.setUseTypeHeaders(false);

        // 역직렬화 실패는 poll() 중 바로 터져서 DefaultErrorHandler/DLQ를 못 타므로 감싸서 넘긴다.
        ErrorHandlingDeserializer<EventEnvelope<T>> deserializer = new ErrorHandlingDeserializer<>(jsonDeserializer);

        return new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(null),
                new StringDeserializer(),
                deserializer
        );
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> containerFactory(ConsumerFactory<String, T> consumerFactory,
                                                                                     CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}

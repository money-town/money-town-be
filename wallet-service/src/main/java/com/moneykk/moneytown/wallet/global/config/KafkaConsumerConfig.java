package com.moneykk.moneytown.wallet.global.config;

import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionCompensationRequestedEvent;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionConfirmedEvent;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionReservedEvent;
import com.moneykk.moneytown.wallet.consumer.dto.UserRegisteredEvent;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

// 이벤트 타입마다 payload 구조가 달라서, 컨슈머마다 별도의 ConsumerFactory/ContainerFactory를 둔다.
@Configuration
public class KafkaConsumerConfig {

    private static final String CONSUMER_DTO_PACKAGE = "com.moneykk.moneytown.wallet.consumer.dto";

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
    public ConsumerFactory<String, SubscriptionReservedEvent> subscriptionReservedConsumerFactory(KafkaProperties kafkaProperties) {
        return consumerFactory(kafkaProperties, SubscriptionReservedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SubscriptionReservedEvent> subscriptionReservedKafkaListenerContainerFactory(
            ConsumerFactory<String, SubscriptionReservedEvent> subscriptionReservedConsumerFactory
    ) {
        return containerFactory(subscriptionReservedConsumerFactory);
    }

    @Bean
    public ConsumerFactory<String, SubscriptionConfirmedEvent> subscriptionConfirmedConsumerFactory(KafkaProperties kafkaProperties) {
        return consumerFactory(kafkaProperties, SubscriptionConfirmedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SubscriptionConfirmedEvent> subscriptionConfirmedKafkaListenerContainerFactory(
            ConsumerFactory<String, SubscriptionConfirmedEvent> subscriptionConfirmedConsumerFactory
    ) {
        return containerFactory(subscriptionConfirmedConsumerFactory);
    }

    @Bean
    public ConsumerFactory<String, SubscriptionCompensationRequestedEvent> subscriptionCompensationRequestedConsumerFactory(
            KafkaProperties kafkaProperties
    ) {
        return consumerFactory(kafkaProperties, SubscriptionCompensationRequestedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SubscriptionCompensationRequestedEvent> subscriptionCompensationRequestedKafkaListenerContainerFactory(
            ConsumerFactory<String, SubscriptionCompensationRequestedEvent> subscriptionCompensationRequestedConsumerFactory
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

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> containerFactory(ConsumerFactory<String, T> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        return factory;
    }
}

package com.moneykk.moneytown.analysis.global.config;

import com.moneykk.moneytown.analysis.fds.infrastructure.kafka.exception.SubscriptionEventDeserializationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class AnalysisKafkaConsumerConfig {

    private static final long INITIAL_INTERVAL_MS = 2_000L;
    private static final double MULTIPLIER = 2.0;
    private static final long MAX_INTERVAL_MS = 8_000L;
    private static final int MAX_RETRY_COUNT = 3;

    @Bean
    public DefaultErrorHandler analysisKafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate){
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRY_COUNT);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MS);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                backOff
        );

        errorHandler.addNotRetryableExceptions(SubscriptionEventDeserializationException.class);

        return errorHandler;
    }
}

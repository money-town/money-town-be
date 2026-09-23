package com.moneykk.moneytown.offering.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic subscriptionConfirmedTopic() {
        return TopicBuilder.name("subscription-confirmed")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic subscriptionConfirmedDltTopic() {
        return TopicBuilder.name("subscription-confirmed-dlt")
                .partitions(6)
                .replicas(1)
                .build();
    }
}

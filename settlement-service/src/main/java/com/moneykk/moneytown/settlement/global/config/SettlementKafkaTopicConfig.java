package com.moneykk.moneytown.settlement.global.config;

import com.moneykk.moneytown.settlement.infrastructure.kafka.event.DividendPayoutDispatchPayload;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// 브로커의 자동 생성 기본값(파티션 1개)으로 만들어지면 컨슈머 동시성이 있어도 병렬 처리가 안 되므로 파티션을 명시해 생성한다.
// DLT는 DeadLetterPublishingRecoverer가 "원본 파티션 번호 그대로" 발행하므로 원본과 같은 파티션 수가 필요하다.
@Configuration
public class SettlementKafkaTopicConfig {

    @Bean
    public NewTopic dividendPayoutDispatchTopic() {
        return TopicBuilder.name(DividendPayoutDispatchPayload.TOPIC)
                .partitions(DividendPayoutDispatchPayload.PARTITIONS)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic dividendPayoutDispatchDltTopic() {
        return TopicBuilder.name(DividendPayoutDispatchPayload.DLT_TOPIC)
                .partitions(DividendPayoutDispatchPayload.PARTITIONS)
                .replicas(1)
                .build();
    }
}
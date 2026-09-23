package com.moneykk.moneytown.wallet.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// 브로커 기본값(파티션 1개)에 의존하지 않도록 wallet 책임 토픽을 명시 생성 (DLT는 원본과 파티션 수 동일해야 함)
@Configuration
public class WalletKafkaTopicConfig {

    @Bean
    public NewTopic walletDividendResultTopic() {
        return TopicBuilder.name("wallet-dividend-result").partitions(4).replicas(1).build();
    }

    @Bean
    public NewTopic dividendPayoutDispatchDltTopic() {
        return TopicBuilder.name("dividend-payout-dispatch-requested-dlt").partitions(8).replicas(1).build();
    }
}

package com.moneykk.moneytown.offering.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxExecutorConfigTest {

    private final OutboxExecutorConfig outboxExecutorConfig =
            new OutboxExecutorConfig();

    @Test
    @DisplayName("발행 스레드풀은 설정된 pool size와 queue capacity로 생성된다")
    void createsPublishExecutorWithConfiguredCapacity() {
        // when
        ThreadPoolTaskExecutor executor =
                outboxExecutorConfig.outboxPublishExecutor(4, 20);
        executor.initialize();

        // then
        assertThat(executor.getCorePoolSize()).isEqualTo(4);
        assertThat(executor.getMaxPoolSize()).isEqualTo(4);
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                .isEqualTo(20);
        assertThat(executor.getThreadNamePrefix())
                .isEqualTo("outbox-publish-");
    }

    @Test
    @DisplayName("발행 콜백 스레드풀은 설정된 core/max pool size와 queue capacity로 생성된다")
    void createsCallbackExecutorWithConfiguredCapacity() {
        // when
        ThreadPoolTaskExecutor executor =
                outboxExecutorConfig.outboxPublishCallbackExecutor(2, 4, 100);
        executor.initialize();

        // then
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(4);
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                .isEqualTo(100);
        assertThat(executor.getThreadNamePrefix())
                .isEqualTo("outbox-callback-");
    }
}

package com.moneykk.moneytown.offering.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class OutboxExecutorConfig {

    @Bean(name = "outboxPublishCallbackExecutor")
    public ThreadPoolTaskExecutor outboxPublishCallbackExecutor(
            @Value("${outbox.publish.callback-core-pool-size:2}")
            int corePoolSize,
            @Value("${outbox.publish.callback-max-pool-size:4}")
            int maxPoolSize,
            @Value("${outbox.publish.callback-queue-capacity:100}")
            int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("outbox-callback-");

        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);

        return executor;
    }
}
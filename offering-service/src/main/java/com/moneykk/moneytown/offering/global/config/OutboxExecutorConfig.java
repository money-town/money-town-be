package com.moneykk.moneytown.offering.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class OutboxExecutorConfig {

    // KafkaTemplate.send() 최초 호출을 스케줄러 스레드에서 분리
    @Bean(name = "outboxPublishExecutor")
    public ThreadPoolTaskExecutor outboxPublishExecutor(
            @Value("${outbox.publish.worker-pool-size:4}")
            int poolSize,
            @Value("${outbox.publish.worker-queue-capacity:20}")
            int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        // 고정 크기 발행 풀
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("outbox-publish-");

        /*
         * CallerRunsPolicy를 사용하면 큐가 가득 찼을 때
         * 스케줄러 스레드가 Kafka 전송을 실행할 수 있으므로 사용하지 않는다.
         */
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);

        return executor;
    }

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

        // 완료 처리도 호출 스레드에서 대신 실행되지 않게 한다.
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);

        return executor;
    }
}
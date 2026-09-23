package com.moneykk.moneytown.analysis.ai.command.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioQueueDispatcher {

    private final PortfolioStore portfolioStore;
    private final PortfolioGenerator portfolioGenerator;
    private final PortfolioNotificationDispatcher portfolioNotificationDispatcher;
    private final ThreadPoolTaskExecutor aiTaskExecutor;

    @Scheduled(fixedDelayString = "${spring.ai.portfolio.dispatch-interval-ms:1000}")
    public void dispatch() {
        int availableSlots = aiTaskExecutor.getMaxPoolSize() - aiTaskExecutor.getActiveCount();
        if (availableSlots <= 0) {
            return;
        }

        List<UUID> ids = portfolioStore.claimBatch(availableSlots);
        for (UUID id : ids) {
            try {
                aiTaskExecutor.execute(() -> {
                    portfolioGenerator.generate(id);
                    portfolioNotificationDispatcher.notify(id);
                });
            } catch (RejectedExecutionException e) {
                log.warn("워커풀 포화로 포트폴리오 {} 제출 실패", id, e);
                portfolioStore.fail(id, "워커풀 포화로 처리하지 못했습니다.", 0L);
            }
        }
    }
}

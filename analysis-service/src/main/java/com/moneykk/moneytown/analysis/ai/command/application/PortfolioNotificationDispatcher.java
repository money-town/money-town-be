package com.moneykk.moneytown.analysis.ai.command.application;

import com.moneykk.moneytown.analysis.ai.domain.AiStatus;
import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.repository.PortfolioRepository;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackDirectMessageSender;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackSendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioNotificationDispatcher {

    private final PortfolioRepository portfolioRepository;
    private final SlackDirectMessageSender slackDirectMessageSender;

    @Async("slackSender")
    public void notify(UUID portfolioId) {
        try {
            Portfolio p = portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId).orElse(null);
            if (p == null || p.getSlackId() == null || p.getSlackId().isBlank()) {
                return;
            }
            boolean completed = p.getStatus() == AiStatus.COMPLETED;
            String title = completed ? "AI 포트폴리오 생성 완료" : "AI 포트폴리오 생성 실패";
            String message = completed
                    ? "요청하신 AI 포트폴리오 생성이 완료되었습니다."
                    : "AI 포트폴리오 생성에 실패했습니다: " + p.getErrorMessage();

            SlackSendResult result = slackDirectMessageSender.send(p.getSlackId(), title, message);
            if (!result.success()) {
                log.warn("포트폴리오 {} DM 발송 실패: {}", portfolioId, result.errorMessage());
            }
        } catch (Exception e) {
            log.error("포트폴리오 {} DM 발송 처리 실패", portfolioId, e);
        }
    }
}

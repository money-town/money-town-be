package com.moneykk.moneytown.analysis.ai.command.application;

import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;
import com.moneykk.moneytown.analysis.ai.domain.repository.PortfolioRepository;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackDirectMessageSender;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioNotificationDispatcherTest {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private SlackDirectMessageSender slackDirectMessageSender;

    private PortfolioNotificationDispatcher dispatcher;

    private final UUID portfolioId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dispatcher = new PortfolioNotificationDispatcher(portfolioRepository, slackDirectMessageSender);
    }

    private Portfolio portfolioWithSlackId(String slackId) {
        Portfolio p = Portfolio.builder()
                .idempotencyKey(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .investmentAmount(1_000_000L)
                .riskType(RiskType.MEDIUM)
                .assetType(null)
                .model("gpt-4o-mini")
                .promptVersion("v1")
                .slackId(slackId)
                .build();
        ReflectionTestUtils.setField(p, "id", portfolioId);
        return p;
    }

    @Test
    @DisplayName("포트폴리오가 없으면 DM을 보내지 않는다")
    void notify_portfolioNotFound_doesNothing() {
        when(portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)).thenReturn(Optional.empty());

        assertThatCode(() -> dispatcher.notify(portfolioId)).doesNotThrowAnyException();

        verifyNoInteractions(slackDirectMessageSender);
    }

    @Test
    @DisplayName("slackId가 없으면 DM을 보내지 않는다")
    void notify_noSlackId_doesNothing() {
        Portfolio p = portfolioWithSlackId(null);
        when(portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)).thenReturn(Optional.of(p));

        dispatcher.notify(portfolioId);

        verifyNoInteractions(slackDirectMessageSender);
    }

    @Test
    @DisplayName("slackId가 공백이면 DM을 보내지 않는다")
    void notify_blankSlackId_doesNothing() {
        Portfolio p = portfolioWithSlackId("   ");
        when(portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)).thenReturn(Optional.of(p));

        dispatcher.notify(portfolioId);

        verify(slackDirectMessageSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("완료 상태면 완료 문구로 DM을 보낸다")
    void notify_completed_sendsCompletionMessage() {
        Portfolio p = portfolioWithSlackId("U123");
        p.process();
        p.complete("{}", 100L);
        when(portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)).thenReturn(Optional.of(p));
        when(slackDirectMessageSender.send(eq("U123"), anyString(), anyString())).thenReturn(SlackSendResult.ok());

        dispatcher.notify(portfolioId);

        verify(slackDirectMessageSender).send(eq("U123"), eq("AI 포트폴리오 생성 완료"), anyString());
    }

    @Test
    @DisplayName("실패 상태면 에러 메시지를 포함한 실패 문구로 DM을 보낸다")
    void notify_failed_sendsFailureMessageWithErrorDetail() {
        Portfolio p = portfolioWithSlackId("U123");
        p.process();
        p.fail("추천 가능한 공모가 없습니다.", 100L);
        when(portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)).thenReturn(Optional.of(p));
        when(slackDirectMessageSender.send(eq("U123"), anyString(), anyString())).thenReturn(SlackSendResult.ok());

        dispatcher.notify(portfolioId);

        verify(slackDirectMessageSender).send(
                eq("U123"), eq("AI 포트폴리오 생성 실패"), org.mockito.ArgumentMatchers.contains("추천 가능한 공모가 없습니다.")
        );
    }

    @Test
    @DisplayName("Slack 발송이 실패 결과를 반환해도 예외를 던지지 않는다")
    void notify_sendReturnsFailure_doesNotThrow() {
        Portfolio p = portfolioWithSlackId("U123");
        p.process();
        p.complete("{}", 100L);
        when(portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)).thenReturn(Optional.of(p));
        when(slackDirectMessageSender.send(any(), any(), any())).thenReturn(SlackSendResult.fail("channel_not_found"));

        assertThatCode(() -> dispatcher.notify(portfolioId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Slack 발송 중 예외가 나도 밖으로 전파하지 않는다")
    void notify_senderThrows_swallowsException() {
        Portfolio p = portfolioWithSlackId("U123");
        p.process();
        p.complete("{}", 100L);
        when(portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)).thenReturn(Optional.of(p));
        when(slackDirectMessageSender.send(any(), any(), any())).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> dispatcher.notify(portfolioId)).doesNotThrowAnyException();
    }
}

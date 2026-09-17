package com.moneykk.moneytown.analysis.notification.command.application;

import com.moneykk.moneytown.analysis.notification.command.application.NotificationDispatcher;
import com.moneykk.moneytown.analysis.notification.command.application.NotificationStore;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackNotificationSender;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private SlackNotificationSender slackSender;
    @Mock
    private NotificationStore notificationStore;

    private NotificationDispatcher dispatcher;

    private final UUID notificationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(slackSender, notificationStore);
    }

    @Test
    @DisplayName("정상 발송되면 그 결과로 완료 처리한다")
    void dispatch_success_completesWithResult() {
        SlackSendResult ok = SlackSendResult.ok();
        when(slackSender.send("제목", "내용")).thenReturn(ok);

        dispatcher.dispatch(notificationId, "제목", "내용");

        verify(notificationStore).complete(notificationId, ok);
    }

    @Test
    @DisplayName("Slack 발송 중 예상치 못한 예외가 나도 밖으로 전파하지 않는다")
    void dispatch_slackSenderThrows_swallowsException() {
        when(slackSender.send(any(), any())).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> dispatcher.dispatch(notificationId, "제목", "내용"))
                .doesNotThrowAnyException();
        verifyNoInteractions(notificationStore);
    }

    @Test
    @DisplayName("완료 처리 중 예외가 나도 밖으로 전파하지 않는다")
    void dispatch_notificationStoreThrows_swallowsException() {
        when(slackSender.send(any(), any())).thenReturn(SlackSendResult.ok());
        doThrow(new RuntimeException("db down")).when(notificationStore).complete(any(), any());

        assertThatCode(() -> dispatcher.dispatch(notificationId, "제목", "내용"))
                .doesNotThrowAnyException();
    }
}

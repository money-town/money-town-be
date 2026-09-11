package com.moneykk.moneytown.analysis.notification.command.application;

import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackNotificationSender;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackSendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 알림 발송을 요청 스레드에서 분리하는 비동기 경계.
 * <p>
 * PENDING 레코드는 호출 측({@link NotificationCommandService})에서 이미 커밋된 상태로 넘어오며,
 * 여기서는 Slack 호출과 그 결과(SENT/FAILED) 기록만 담당한다.
 * {@code @Async} 프록시가 적용되려면 반드시 다른 빈에서 호출되어야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final SlackNotificationSender slackSender;
    private final NotificationStore notificationStore;

    @Async("slackSender")
    public void dispatch(UUID notificationId, String title, String message) {
        try {
            SlackSendResult result = slackSender.send(title, message);
            notificationStore.complete(notificationId, result);
        } catch (Exception e) {
            // @Async void 밖으로 예외가 나가면 기본 핸들러가 밋밋하게만 남기므로 여기서 컨텍스트와 함께 로깅한다.
            log.error("알림 발송 처리 실패 notificationId={}", notificationId, e);
        }
    }
}

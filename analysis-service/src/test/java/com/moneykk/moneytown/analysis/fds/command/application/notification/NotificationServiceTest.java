package com.moneykk.moneytown.analysis.fds.command.application.notification;

import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.analysis.notification.command.application.NotificationCommandService;
import com.moneykk.moneytown.analysis.notification.command.application.NotificationStore;
import com.moneykk.moneytown.analysis.notification.command.dto.request.NotificationRequest;
import com.moneykk.moneytown.analysis.notification.command.dto.response.NotificationResponse;
import com.moneykk.moneytown.analysis.notification.domain.Notification;
import com.moneykk.moneytown.analysis.notification.domain.NotificationStatus;
import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackNotificationSender;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackSendResult;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationStore notificationStore;
    @Mock
    private SlackNotificationSender notificationSender;


    @InjectMocks
    private NotificationCommandService notificationCommandService;

    private final UUID idempotencyKey = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();
    private final String TITLE = "발송 테스트 입니다.";
    private final String MESSAGE = "안녕하세요. 테스트 중입니다.";



    @Test
    @DisplayName("새 멱등키면 알림을 선점·발송 하고 완료 결과를 반환한다.")
    void send_newIdempotencyKey_delegatesAndReturnResponse(){
        Notification claimed = pendingNotification();
        Notification finished = pendingNotification();
        finished.markSent();
        SlackSendResult ok = SlackSendResult.ok();

        when(notificationStore.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(notificationStore.claim(any(Notification.class))).thenReturn(claimed);
        when(notificationSender.send(TITLE, MESSAGE)).thenReturn(ok);
        when(notificationStore.complete(notificationId, ok)).thenReturn(finished);

        NotificationResponse response = notificationCommandService.send(
                idempotencyKey,
                new NotificationRequest(NotificationType.SLACK_TEST, null, TITLE, MESSAGE)
        );

        assertThat(response.notificationId()).isEqualTo(notificationId);
        assertThat(response.notificationType()).isEqualTo(NotificationType.SLACK_TEST);
        assertThat(response.status()).isEqualTo(NotificationStatus.SENT);
        verify(notificationSender).send(TITLE, MESSAGE);
    }


    @Test
    @DisplayName("이미 처리된 명등키면 Slack 호출 없이 기존 결과를 반환한다.")
    void send_duplicateIdempotencyKey_returnsExistingWithoutSending(){
        Notification existing = pendingNotification();
        existing.markSent();
        when(notificationStore.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        NotificationResponse response = notificationCommandService.send(
                idempotencyKey,
                new NotificationRequest(NotificationType.SLACK_TEST, null, TITLE, MESSAGE)
        );

        assertThat(response.notificationId()).isEqualTo(notificationId);
        verify(notificationStore, never()).claim(any());
        verify(notificationSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("Slack 발송에 실패해도 예외 없이 FAILED로 기록된 결과를 반환한다.")
    void send_slackSendFails_recordFailedAnyReturnsNormally(){
        Notification claimed = pendingNotification();
        Notification failed = pendingNotification();
        failed.markFail("timeout");
        SlackSendResult fail = SlackSendResult.fail("timeout");

        when(notificationStore.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(notificationStore.claim(any(Notification.class))).thenReturn(claimed);
        when(notificationSender.send(TITLE, MESSAGE)).thenReturn(fail);
        when(notificationStore.complete(failed.getId(), fail)).thenReturn(failed);

        NotificationResponse response = notificationCommandService.send(
                idempotencyKey, request()
        );

        assertThat(response.status()).isEqualTo(NotificationStatus.FAILED);
        verify(notificationSender).send(TITLE, MESSAGE);
    }

    @Test
    @DisplayName("동시요청으로 claim이 제약 위반이면 제조회한 기존 결과를 반환한다 (멱등)")
    void send_claimConflict_returnsExistingFromRelookup(){
        Notification existing = pendingNotification();
        existing.markSent();

        when(notificationStore.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(notificationStore.claim(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        NotificationResponse response = notificationCommandService.send(idempotencyKey, request());

        assertThat(response.notificationId()).isEqualTo(notificationId);
        verify(notificationSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("claim이 제약 위반인데 재조회도 비어있으면 NOTIFICATION_DUPLICATE_REQUEST 예외")
    void send_claimConflictButRelookup_throwsDuplicate(){
        when(notificationStore.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(notificationStore.claim(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> notificationCommandService.send(idempotencyKey, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnalysisErrorCode.NOTIFICATION_DUPLICATE_REQUEST);
    }



    private NotificationRequest request() {
        return new NotificationRequest(NotificationType.SLACK_TEST, null, TITLE, MESSAGE);
    }

    private Notification pendingNotification(){
        Notification notification = Notification.builder()
                .idempotencyKey(idempotencyKey)
                .notificationType(NotificationType.SLACK_TEST)
                .userId(null)
                .title(TITLE)
                .message(MESSAGE)
                .build();
        ReflectionTestUtils.setField(notification, "id", notificationId);
        return notification;
    }
}

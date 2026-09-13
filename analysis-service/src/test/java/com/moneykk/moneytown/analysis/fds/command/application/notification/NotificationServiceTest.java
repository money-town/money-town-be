package com.moneykk.moneytown.analysis.fds.command.application.notification;

import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.analysis.notification.command.application.NotificationCommandService;
import com.moneykk.moneytown.analysis.notification.command.application.NotificationDispatcher;
import com.moneykk.moneytown.analysis.notification.command.application.NotificationStore;
import com.moneykk.moneytown.analysis.notification.command.dto.request.NotificationRequest;
import com.moneykk.moneytown.analysis.notification.command.dto.response.NotificationResponse;
import com.moneykk.moneytown.analysis.notification.domain.Notification;
import com.moneykk.moneytown.analysis.notification.domain.NotificationStatus;
import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
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
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationStore notificationStore;
    @Mock
    private NotificationDispatcher notificationDispatcher;


    @InjectMocks
    private NotificationCommandService notificationCommandService;

    private final UUID idempotencyKey = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();
    private final String TITLE = "발송 테스트 입니다.";
    private final String MESSAGE = "안녕하세요. 테스트 중입니다.";



    @Test
    @DisplayName("새 멱등키면 알림을 선점하고 발송을 비동기로 위임한 뒤 PENDING 스냅샷을 반환한다.")
    void send_newIdempotencyKey_delegatesAndReturnResponse(){
        Notification claimed = pendingNotification();

        when(notificationStore.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(notificationStore.claim(any(Notification.class))).thenReturn(claimed);

        NotificationResponse response = notificationCommandService.send(
                idempotencyKey,
                new NotificationRequest(NotificationType.SLACK_TEST, null, TITLE, MESSAGE)
        );

        assertThat(response.notificationId()).isEqualTo(notificationId);
        assertThat(response.notificationType()).isEqualTo(NotificationType.SLACK_TEST);
        assertThat(response.status()).isEqualTo(NotificationStatus.PENDING);
        verify(notificationDispatcher).dispatch(notificationId, TITLE, MESSAGE);
    }


    @Test
    @DisplayName("이미 처리된 멱등키면 발송 위임 없이 기존 결과를 반환한다.")
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
        verify(notificationDispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    @DisplayName("발송 스레드풀이 포화되어 비동기 위임이 거부되면 FAILED로 마감한다.")
    void send_dispatchRejected_marksFailed(){
        Notification claimed = pendingNotification();

        when(notificationStore.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty(), Optional.of(claimed));
        when(notificationStore.claim(any(Notification.class))).thenReturn(claimed);
        doThrow(new RejectedExecutionException("queue full"))
                .when(notificationDispatcher).dispatch(any(), any(), any());

        NotificationResponse response = notificationCommandService.send(idempotencyKey, request());

        assertThat(response.notificationId()).isEqualTo(notificationId);
        verify(notificationStore).complete(eq(notificationId), argThat(r -> !r.success()));
    }

    @Test
    @DisplayName("동시요청으로 claim이 제약 위반이면 재조회한 기존 결과를 반환한다 (멱등)")
    void send_claimConflict_returnsExistingFromRelookup(){
        Notification existing = pendingNotification();
        existing.markSent();

        when(notificationStore.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(notificationStore.claim(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        NotificationResponse response = notificationCommandService.send(idempotencyKey, request());

        assertThat(response.notificationId()).isEqualTo(notificationId);
        verify(notificationDispatcher, never()).dispatch(any(), any(), any());
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

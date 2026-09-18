package com.moneykk.moneytown.analysis.notification.command.application;

import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.analysis.notification.command.application.NotificationStore;
import com.moneykk.moneytown.analysis.notification.domain.Notification;
import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import com.moneykk.moneytown.analysis.notification.domain.repository.NotificationRepository;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackSendResult;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationStoreTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationStore store;

    private final UUID idempotencyKey = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        store = new NotificationStore(notificationRepository);
    }

    private Notification notification() {
        Notification n = Notification.builder()
                .idempotencyKey(idempotencyKey)
                .notificationType(NotificationType.SLACK_TEST)
                .userId(null)
                .title("제목")
                .message("내용")
                .build();
        ReflectionTestUtils.setField(n, "id", notificationId);
        return n;
    }

    @Test
    @DisplayName("findByIdempotencyKey는 리포지토리에 그대로 위임한다")
    void findByIdempotencyKey_delegates() {
        Notification n = notification();
        when(notificationRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(n));

        assertThat(store.findByIdempotencyKey(idempotencyKey)).contains(n);
    }

    @Test
    @DisplayName("claim은 saveAndFlush로 위임한다")
    void claim_delegatesToSaveAndFlush() {
        Notification n = notification();
        when(notificationRepository.saveAndFlush(n)).thenReturn(n);

        assertThat(store.claim(n)).isEqualTo(n);
    }

    @Test
    @DisplayName("성공 결과면 completeIfPending을 호출하고 최신 상태를 조회해 반환한다")
    void complete_successResult_callsCompleteIfPending() {
        Notification n = notification();
        when(notificationRepository.completeIfPending(eq(notificationId), any())).thenReturn(1);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(n));

        Notification result = store.complete(notificationId, SlackSendResult.ok());

        assertThat(result).isEqualTo(n);
        verify(notificationRepository).completeIfPending(eq(notificationId), any());
    }

    @Test
    @DisplayName("실패 결과면 에러 메시지와 함께 failIfPending을 호출한다")
    void complete_failureResult_callsFailIfPendingWithErrorMessage() {
        Notification n = notification();
        when(notificationRepository.failIfPending(eq(notificationId), eq("전송 실패"), any())).thenReturn(1);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(n));

        store.complete(notificationId, SlackSendResult.fail("전송 실패"));

        verify(notificationRepository).failIfPending(eq(notificationId), eq("전송 실패"), any());
    }

    @Test
    @DisplayName("PENDING 상태가 아니어서 갱신이 0건이어도 최신 상태를 조회해 반환한다")
    void complete_alreadyProcessed_stillReturnsCurrentState() {
        Notification n = notification();
        when(notificationRepository.completeIfPending(eq(notificationId), any())).thenReturn(0);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(n));

        Notification result = store.complete(notificationId, SlackSendResult.ok());

        assertThat(result).isEqualTo(n);
    }

    @Test
    @DisplayName("갱신 후 재조회했는데도 없으면 NOTIFICATION_NOT_FOUND 예외를 던진다")
    void complete_notFoundAfterUpdate_throwsNotFound() {
        when(notificationRepository.completeIfPending(eq(notificationId), any())).thenReturn(1);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store.complete(notificationId, SlackSendResult.ok()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnalysisErrorCode.NOTIFICATION_NOT_FOUND);
    }
}

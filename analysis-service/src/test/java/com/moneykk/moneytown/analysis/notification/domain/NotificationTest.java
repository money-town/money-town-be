package com.moneykk.moneytown.analysis.notification.domain;

import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private final UUID idempotencyKey = UUID.randomUUID();

    private Notification.NotificationBuilder validBuilder() {
        return Notification.builder()
                .idempotencyKey(idempotencyKey)
                .notificationType(NotificationType.SLACK_TEST)
                .userId(null)
                .title("제목")
                .message("내용");
    }

    @Test
    @DisplayName("멱등키가 없으면 생성할 수 없다")
    void build_withoutIdempotencyKey_throws() {
        assertThatThrownBy(() -> Notification.builder()
                .notificationType(NotificationType.SLACK_TEST)
                .title("제목").message("내용").build())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("알림 유형이 없으면 생성할 수 없다")
    void build_withoutNotificationType_throws() {
        assertThatThrownBy(() -> Notification.builder()
                .idempotencyKey(idempotencyKey)
                .title("제목").message("내용").build())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("제목이 비어있으면 생성할 수 없다")
    void build_blankTitle_throws() {
        assertThatThrownBy(() -> Notification.builder()
                .idempotencyKey(idempotencyKey)
                .notificationType(NotificationType.SLACK_TEST)
                .title("  ").message("내용").build())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("내용이 비어있으면 생성할 수 없다")
    void build_blankMessage_throws() {
        assertThatThrownBy(() -> Notification.builder()
                .idempotencyKey(idempotencyKey)
                .notificationType(NotificationType.SLACK_TEST)
                .title("제목").message("").build())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("정상 생성하면 PENDING 상태로 시작한다")
    void build_valid_startsAsPending() {
        Notification n = validBuilder().build();

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("PENDING 상태에서 markSent하면 SENT로 전이되고 발송 시각이 기록된다")
    void markSent_fromPending_transitionsToSent() {
        Notification n = validBuilder().build();

        n.markSent();

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("PENDING이 아니면 markSent할 수 없다")
    void markSent_notPending_throws() {
        Notification n = validBuilder().build();
        n.markSent();

        assertThatThrownBy(n::markSent).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("PENDING 상태에서 markFail하면 FAILED로 전이되고 에러 메시지가 기록된다")
    void markFail_fromPending_transitionsToFailed() {
        Notification n = validBuilder().build();

        n.markFail("전송 실패");

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(n.getErrorMessage()).isEqualTo("전송 실패");
    }

    @Test
    @DisplayName("PENDING이 아니면 markFail할 수 없다")
    void markFail_notPending_throws() {
        Notification n = validBuilder().build();
        n.markFail("먼저 실패");

        assertThatThrownBy(() -> n.markFail("다시 실패")).isInstanceOf(BusinessException.class);
    }
}

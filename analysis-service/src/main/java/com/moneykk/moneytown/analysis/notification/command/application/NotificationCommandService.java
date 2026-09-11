package com.moneykk.moneytown.analysis.notification.command.application;

import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.analysis.notification.command.dto.request.NotificationRequest;
import com.moneykk.moneytown.analysis.notification.command.dto.request.NotificationTestRequest;
import com.moneykk.moneytown.analysis.notification.command.dto.response.NotificationResponse;
import com.moneykk.moneytown.analysis.notification.domain.Notification;
import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackNotificationSender;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackSendResult;
import com.moneykk.moneytown.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationStore notificationStore;
    private final SlackNotificationSender notificationSender;
    private final NotificationDispatcher notificationDispatcher;

    /**
     * 관리자(ADMIN) Slack 연동 테스트.
     * 중복 {@code Idempotency-Key}는 409로 응답하고, Webhook 실패는 FAILED로 기록한 뒤 502로 응답한다.
     */
    public NotificationResponse sendTest(UUID idempotencyKey, NotificationTestRequest request) {
        // 중복 요청(동시요청x)은 선조회로 빠르게 걸러 불필요한 Slack 호출을 막는다
        notificationStore.findByIdempotencyKey(idempotencyKey).ifPresent(n -> {
            throw new BusinessException(AnalysisErrorCode.NOTIFICATION_DUPLICATE_REQUEST);
        });

        Notification notification;
        try {
            notification = notificationStore.claim(
                    Notification.builder()
                            .idempotencyKey(idempotencyKey)
                            .notificationType(NotificationType.SLACK_TEST)
                            .userId(null)
                            .title(request.title())
                            .message(request.message())
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            // 동시 요청이 먼저 같은 멱등키를 선점 → 중복 요청
            throw new BusinessException(AnalysisErrorCode.NOTIFICATION_DUPLICATE_REQUEST);
        }

        SlackSendResult result = notificationSender.send(request.title(), request.message());
        Notification finished = notificationStore.complete(notification.getId(), result);
        if (!result.success()) {
            throw new BusinessException(AnalysisErrorCode.NOTIFICATION_SLACK_SEND_FAILED);
        }
        return NotificationResponse.from(finished);
    }

    /**
     * 서비스 간 알림 요청(OpenFeign 대상).
     * 중복 {@code Idempotency-Key}는 기존 결과를 그대로 반환한다.
     * PENDING 레코드를 별도 트랜잭션으로 커밋한 뒤 Slack 발송은 {@link NotificationDispatcher}에 비동기로 위임하므로,
     * 이 메서드는 즉시 PENDING 스냅샷을 반환하며 호출 측 응답을 Slack 응답 시간만큼 지연시키지 않는다.
     */
    public NotificationResponse send(UUID idempotencyKey, NotificationRequest request) {
        Notification existing = notificationStore.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return NotificationResponse.from(existing);
        }

        Notification notification;
        try {
            notification = notificationStore.claim(
                    Notification.builder()
                            .idempotencyKey(idempotencyKey)
                            .notificationType(request.notificationType())
                            .userId(request.userId())
                            .title(request.title())
                            .message(request.message())
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            // 동시 요청이 먼저 선점 → 기존 결과 반환 (멱등)
            return notificationStore.findByIdempotencyKey(idempotencyKey)
                    .map(NotificationResponse::from)
                    .orElseThrow(() -> new BusinessException(AnalysisErrorCode.NOTIFICATION_DUPLICATE_REQUEST));
        }

        try {
            notificationDispatcher.dispatch(notification.getId(), request.title(), request.message());
        } catch (RejectedExecutionException e) {
            // 발송 스레드풀 포화(AbortPolicy) → PENDING 고아 레코드가 남지 않도록 FAILED로 마감한다.
            notificationStore.complete(notification.getId(), SlackSendResult.fail("발송 큐 포화(REJECTED): " + e.getMessage()));
            return NotificationResponse.from(
                    notificationStore.findByIdempotencyKey(idempotencyKey).orElse(notification)
            );
        }

        return NotificationResponse.from(notification);
    }
}

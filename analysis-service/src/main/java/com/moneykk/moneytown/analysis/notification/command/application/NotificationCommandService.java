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

@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationStore notificationStore;
    private final SlackNotificationSender notificationSender;

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
     * 중복 {@code Idempotency-Key}는 기존 결과를 그대로 반환하고, Webhook 실패는 FAILED로 기록한 뒤 정상 응답한다
     * (호출 측 업무 트랜잭션에 영향을 주지 않는다).
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
            return NotificationResponse.from(
                    notificationStore.findByIdempotencyKey(idempotencyKey)
                            .orElseThrow(() -> new BusinessException(AnalysisErrorCode.NOTIFICATION_DUPLICATE_REQUEST))
            );
        }

        SlackSendResult result = notificationSender.send(request.title(), request.message());
        Notification finished = notificationStore.complete(notification.getId(), result);
        return NotificationResponse.from(finished);
    }
}

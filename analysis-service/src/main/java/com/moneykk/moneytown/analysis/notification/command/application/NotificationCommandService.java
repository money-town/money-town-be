package com.moneykk.moneytown.analysis.notification.command.application;

import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.analysis.notification.command.dto.request.NotificationRequest;
import com.moneykk.moneytown.analysis.notification.command.dto.request.NotificationTestRequest;
import com.moneykk.moneytown.analysis.notification.command.dto.response.NotificationResponse;
import com.moneykk.moneytown.analysis.notification.domain.Notification;
import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import com.moneykk.moneytown.analysis.notification.domain.repository.NotificationRepository;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackNotificationSender;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackSendResult;
import com.moneykk.moneytown.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final SlackNotificationSender notificationSender;



    @Transactional(noRollbackFor = BusinessException.class)
    public NotificationResponse sendTest(UUID idempotencyKey, NotificationTestRequest request) {
        notificationRepository.findByIdempotencyKey(idempotencyKey).ifPresent(n ->{
            throw new BusinessException(AnalysisErrorCode.NOTIFICATION_DUPLICATE_REQUEST);
        });

        Notification notification = notificationRepository.save(
                Notification.builder()
                        .idempotencyKey(idempotencyKey)
                        .notificationType(NotificationType.SLACK_TEST)
                        .userId(null)
                        .title(request.title())
                        .message(request.message())
                        .build()
        );


        SlackSendResult result = notificationSender.send(request.title(), request.message());
        if(!result.success()){
            notification.markFail(result.errorMessage());
            notificationRepository.save(notification);
            throw new BusinessException(AnalysisErrorCode.NOTIFICATION_SLACK_SEND_FAILED);
        }

        notification.markSent();
        notificationRepository.save(notification);
        return NotificationResponse.from(notification);
    }

    @Transactional
    public NotificationResponse send(UUID idempotencyKey, NotificationRequest request) {
        Notification existing = notificationRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return NotificationResponse.from(existing);
        }

        Notification notification = notificationRepository.save(
                Notification.builder()
                        .idempotencyKey(idempotencyKey)
                        .notificationType(request.notificationType())
                        .userId(request.userId())
                        .title(request.title())
                        .message(request.message())
                        .build()
        );

        SlackSendResult result = notificationSender.send(request.title(), request.message());
        if (result.success()) {
            notification.markSent();
        } else {
            notification.markFail(result.errorMessage());
        }
        notificationRepository.save(notification);
        return NotificationResponse.from(notification);

    }
}

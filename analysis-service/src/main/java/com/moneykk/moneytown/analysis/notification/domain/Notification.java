package com.moneykk.moneytown.analysis.notification.domain;

import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import com.moneykk.moneytown.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Table(name = "p_slack_notifications")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "notification_id")
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    // null = 운영(ADMIN) Slack 채널 발송, non-null = 해당 유저 발송
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Builder
    private Notification(UUID idempotencyKey, UUID userId, NotificationType notificationType, String title, String message){
        // 필수값 검증
        if (idempotencyKey == null) {
            throw new BusinessException(AnalysisErrorCode.NOTIFICATION_IDEMPOTENCY_KEY_REQUIRED);
        }
        if (notificationType == null) {
            throw new BusinessException(AnalysisErrorCode.NOTIFICATION_TYPE_REQUIRED);
        }
        if (title == null || title.isBlank()) {
            throw new BusinessException(AnalysisErrorCode.NOTIFICATION_TITLE_REQUIRED);
        }
        if (message == null || message.isBlank()) {
            throw new BusinessException(AnalysisErrorCode.NOTIFICATION_MESSAGE_REQUIRED);
        }
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.notificationType = notificationType;
        this.title = title;
        this.message = message;
        this.status = NotificationStatus.PENDING;
    }

    public void sent(){
        if(this.status != NotificationStatus.PENDING){
            throw new BusinessException(AnalysisErrorCode.NOTIFICATION_ALREADY_FINISHED);
        }
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void fail(String errorMessage){
        if(this.status != NotificationStatus.PENDING){
            throw new BusinessException(AnalysisErrorCode.NOTIFICATION_ALREADY_FINISHED);
        }
        this.status = NotificationStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}

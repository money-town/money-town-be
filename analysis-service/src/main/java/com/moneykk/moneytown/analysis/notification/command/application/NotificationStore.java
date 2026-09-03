package com.moneykk.moneytown.analysis.notification.command.application;

import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.analysis.notification.domain.Notification;
import com.moneykk.moneytown.analysis.notification.domain.repository.NotificationRepository;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackSendResult;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 알림 레코드의 트랜잭션 경계를 담당한다.
 * 멱등키 선점(claim)과 발송 결과 기록(complete)을 각각 독립 트랜잭션으로 커밋해,
 * Slack 발송을 그 사이에서 트랜잭션 밖으로 분리한다.
 */
@Component
@RequiredArgsConstructor
public class NotificationStore {

    private final NotificationRepository notificationRepository;

    public Optional<Notification> findByIdempotencyKey(UUID idempotencyKey) {
        return notificationRepository.findByIdempotencyKey(idempotencyKey);
    }

    /**
     * 멱등키로 알림을 원자적으로 선점한다.
     * 동시 요청이 이미 같은 키를 저장했다면 unique 제약 위반으로 {@link DataIntegrityViolationException}이 발생한다.
     * 이 트랜잭션에는 INSERT만 있으므로 예외 발생 시 깔끔히 롤백되고 호출자로 전파된다.
     */
    @Transactional
    public Notification claim(Notification notification) {
        return notificationRepository.saveAndFlush(notification);
    }

    /** 발송 결과(SENT/FAILED)를 별도 트랜잭션으로 기록한다. */
    @Transactional
    public Notification complete(UUID notificationId, SlackSendResult result) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(AnalysisErrorCode.NOTIFICATION_NOT_FOUND));
        if (result.success()) {
            notification.markSent();
        } else {
            notification.markFail(result.errorMessage());
        }
        return notification;
    }
}

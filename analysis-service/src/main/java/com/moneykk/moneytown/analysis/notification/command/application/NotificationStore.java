package com.moneykk.moneytown.analysis.notification.command.application;

import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.analysis.notification.domain.Notification;
import com.moneykk.moneytown.analysis.notification.domain.repository.NotificationRepository;
import com.moneykk.moneytown.analysis.notification.infrastructure.slack.SlackSendResult;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 알림 레코드의 트랜잭션 경계를 담당한다.
 * 멱등키 선점(claim)과 발송 결과 기록(complete)을 각각 독립 트랜잭션으로 커밋해,
 * Slack 발송을 그 사이에서 트랜잭션 밖으로 분리한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
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

    /**
     * 발송 결과(SENT/FAILED)를 별도 트랜잭션으로 기록한다.
     * id + status(PENDING)를 조건으로 건 원자적 UPDATE라서, {@link NotificationStaleReaper}의
     * 벌크 정리와 동시에 실행돼도 늦게 도착한 쪽이 먼저 끝난 쪽을 덮어쓰지 않는다.
     * (PENDING이 아니면 0건 갱신 → 이미 처리된 것으로 보고 조용히 무시)
     */
    @Transactional
    public Notification complete(UUID notificationId, SlackSendResult result) {
        int updated = result.success()
                ? notificationRepository.completeIfPending(notificationId, Instant.now())
                : notificationRepository.failIfPending(notificationId, result.errorMessage(), Instant.now());

        if (updated == 0) {
            log.warn("알림 {} 가 PENDING 상태가 아니라 complete를 무시합니다 (이미 처리됨).", notificationId);
        }

        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(AnalysisErrorCode.NOTIFICATION_NOT_FOUND));
    }
}

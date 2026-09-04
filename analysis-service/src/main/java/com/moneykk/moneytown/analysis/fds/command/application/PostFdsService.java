package com.moneykk.moneytown.analysis.fds.command.application;

import com.moneykk.moneytown.analysis.fds.command.redis.PostFdsCounter;
import com.moneykk.moneytown.analysis.fds.command.redis.PostFdsCounts;
import com.moneykk.moneytown.analysis.fds.domain.EventType;
import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import com.moneykk.moneytown.analysis.fds.domain.UserStatus;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsDetectionLogRepository;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsUserStateRepository;
import com.moneykk.moneytown.analysis.fds.infrastructure.kafka.event.SubscriptionEventPayload;
import com.moneykk.moneytown.analysis.global.config.PostFdsRuleProperties;
import com.moneykk.moneytown.analysis.notification.command.application.NotificationCommandService;
import com.moneykk.moneytown.analysis.notification.command.dto.request.NotificationRequest;
import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import com.moneykk.moneytown.common.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostFdsService {

    private final PostFdsDetectionApplier postFdsDetectionApplier;
    private final NotificationCommandService notificationCommandService;
    private final PostFdsRuleProperties ruleProperties;
    private final PostFdsCounter postFdsCounter;
    private final FdsUserStateRepository fdsUserStateRepository;
    private final FdsDetectionLogRepository fdsDetectionLogRepository;

    public void handle(EventEnvelope<SubscriptionEventPayload> envelope){
        UUID eventId = envelope.eventId();
        SubscriptionEventPayload payload = envelope.payload();
        UUID userId = payload.userId();
        EventType eventType = EventType.valueOf(envelope.eventType());


        // 1. 멱등
        if(fdsDetectionLogRepository.existsByEventId(eventId)){
            log.info("이미 처리된 이벤트 skip eventId= {}", eventId);
            return;
        }


        // 2. 현재 상태 (row 없으면 NORMAL)
        UserStatus status = fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)
                .map(FdsUserState::getStatus)
                .orElse(UserStatus.NORMAL);


        // 3. BLOCKED는 더 볼 거 없음
        if(status == UserStatus.BLOCKED){
            log.info("이미 Block 처리된 유저의 요청입니다 userId= {}" , userId);
            return;
        }

        // 4. 누적 집계
        PostFdsCounts counts = postFdsCounter.recordAndCount(userId, eventId, eventType, envelope.occurredAt());


        // 5. 첫 위반 룰
        RuleCode violated = firstViolation(counts);
        if(violated == null){
            return;
        }
        // 6. 탐지 반영(트랜잭션 커밋)
        int observed = observedValue(violated, counts);
        int threshold = ruleProperties.get(violated).threshold();
        UserStatus newStatus;
        try{
            newStatus = postFdsDetectionApplier.apply(
                    userId, eventId, payload.assetId(), envelope.occurredAt(), eventType, violated, observed, threshold
            );
        }catch (DataIntegrityViolationException e){
            log.info("이미 중복 처리된 이벤트 입니다. eventId={}", eventId);
            return;
        }

        // 7. SUSPICIOUS->BLOCKED 전이일 때만 알림 (실패 격리)
        if(newStatus == UserStatus.BLOCKED){
            notifyBlocked(userId, eventId, payload, violated, observed, threshold);
        }
    }

    private void notifyBlocked(UUID userId, UUID eventId, SubscriptionEventPayload payload, RuleCode rule, int observed, int threshold) {
        try {
            notificationCommandService.send(
                    eventId,   // 멱등키 = eventId
                    new NotificationRequest(
                            NotificationType.FDS_BLOCKED,
                            userId,
                            "FDS 사용자 차단 발생(Post)",
                            """
                            누적 이상 행동으로 사용자가 차단되었습니다.
                            + userId: %s
                            + rule: %s
                            + 측정값 / 임계값 : %d / %d
                            + assetId: %s
                            + eventId: %s""".formatted(
                                    userId, rule, observed, threshold, payload.assetId(), eventId)
                    )
            );
        } catch (Exception e) {
            log.error("post-fds 차단 알림 발송 실패 userId={}, eventId={}", userId, eventId, e);
        }
    }

    private RuleCode firstViolation(PostFdsCounts counts) {
        if(counts.failCount() >= ruleProperties.get(RuleCode.REPEATED_FAILURE).threshold()){
            return RuleCode.REPEATED_FAILURE;
        }
        if (counts.limitExceededCount() >= ruleProperties.get(RuleCode.REPEATED_LIMIT_EXCEEDED).threshold()) {
            return RuleCode.REPEATED_LIMIT_EXCEEDED;
        }

        PostFdsRuleProperties.PostThreshold hcr = ruleProperties.get(RuleCode.HIGH_CANCEL_RATE);
        if(counts.recentSize() >= hcr.sampleSize() && counts.cancelledCount() >= hcr.threshold()){
            return RuleCode.HIGH_CANCEL_RATE;
        }

        return null;
    }
    private int observedValue(RuleCode rule, PostFdsCounts counts){
        int result = 0;
        switch (rule){
            case REPEATED_FAILURE -> result = (int)counts.failCount();
            case REPEATED_LIMIT_EXCEEDED -> result = (int)counts.limitExceededCount();
            case HIGH_CANCEL_RATE -> result = (int) counts.cancelledCount();
            default -> {}
        }
        return result;
    }

}

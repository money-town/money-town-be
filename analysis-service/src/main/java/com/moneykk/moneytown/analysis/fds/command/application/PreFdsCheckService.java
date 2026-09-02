package com.moneykk.moneytown.analysis.fds.command.application;

import com.moneykk.moneytown.analysis.fds.command.dto.request.PreFdsCheckRequest;
import com.moneykk.moneytown.analysis.fds.command.dto.response.PreFdsCheckResult;
import com.moneykk.moneytown.analysis.fds.command.redis.FdsCheckIdempotencyStore;
import com.moneykk.moneytown.analysis.fds.command.redis.FdsCounts;
import com.moneykk.moneytown.analysis.fds.command.redis.FdsRedisCounter;
import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import com.moneykk.moneytown.analysis.fds.domain.UserStatus;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsUserStateRepository;
import com.moneykk.moneytown.analysis.global.config.FdsRuleProperties;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.analysis.notification.command.application.NotificationCommandService;
import com.moneykk.moneytown.analysis.notification.command.dto.request.NotificationRequest;
import com.moneykk.moneytown.analysis.notification.domain.NotificationType;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreFdsCheckService {

    private final FdsCheckIdempotencyStore idempotencyStore;
    private final FdsRedisCounter redisCounter;
    private final FdsUserStateRepository fdsUserStateRepository;
    private final FdsRuleProperties ruleProperties;
    private final FdsBlockApplier fdsBlockApplier;
    private final NotificationCommandService notificationCommandService;

    public PreFdsCheckResult check(PreFdsCheckRequest request){
        UUID requestId = request.requestId();

        try {
            Optional<PreFdsCheckResult> cached = idempotencyStore.find(requestId);
            // 1. 멱등 처리
            if (cached.isPresent()) return cached.get();
            // 2. 선점
            if (!idempotencyStore.tryBegin(requestId)) {
                return idempotencyStore.find(requestId)
                        .orElseThrow(() -> new BusinessException(AnalysisErrorCode.FDS_UNAVAILABLE));
            }
            // 3. 결과 생성 및 처리
            PreFdsCheckResult result = evaluate(request.userId(), requestId, request.assetId());
            idempotencyStore.complete(requestId, result);
            return result;
        } catch (BusinessException e) {
            idempotencyStore.abort(requestId);   // 이미 시작했으면 마커 정리
            throw e;
        } catch (Exception e) {
            idempotencyStore.abort(requestId);
            throw new BusinessException(AnalysisErrorCode.FDS_UNAVAILABLE);
        }
    }

    private PreFdsCheckResult evaluate(UUID userId,UUID requestId,UUID assetId) {
        UserStatus status = fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)
                .map(FdsUserState::getStatus)
                .orElse(UserStatus.NORMAL);     // row 없으면 NORMAL 취급

        if(status == UserStatus.BLOCKED){
            return PreFdsCheckResult.alreadyBlocked();
        }

        FdsCounts counts = redisCounter.recordAndCount(userId, requestId, assetId);

        RuleViolation violation = firstViolation(status, counts);
        if(violation == null){
            return PreFdsCheckResult.pass();
        }

        fdsBlockApplier.applyBlock(userId, requestId, assetId, violation.rule, violation.observed, violation.threshold);
        // 차단 트랜잭션은 위에서 이미 커밋됨. 알림 실패는 삼켜서 FDS 검사 응답에 영향 X
        notifyBlocked(userId, requestId, assetId, violation);
        return PreFdsCheckResult.block(violation.rule);
    }

    private void notifyBlocked(UUID userId, UUID requestId, UUID assetId, RuleViolation violation) {
        try {
            notificationCommandService.send(
                    requestId,                       // 멱등키 = FDS requestId
                    new NotificationRequest(
                            NotificationType.FDS_BLOCKED,
                            userId,                  // 컨텍스트용 (라우팅 아님)
                            "FDS 사용자 차단 발생",
                            buildBlockMessage(userId, assetId, requestId, violation)
                    )
            );
        } catch (Exception e) {
            log.error("FDS 차단 알림 발송 실패 userId={}, requestId={}", userId, requestId, e);
        }
    }

    private String buildBlockMessage(UUID userId, UUID assetId, UUID requestId, RuleViolation v) {
        return """
                사용자가 이상 청약 행위로 차단되었습니다.
                + userId: %s
                + rule: %s
                + 측정값 / 임계값 : %d / %d
                + assetId: %s
                + requestId: %s""".formatted(
                userId, v.rule(), v.observed(), v.threshold(), assetId, requestId);
    }

    private RuleViolation firstViolation(UserStatus status, FdsCounts counts) {
        int rapidT = ruleProperties.get(RuleCode.RAPID_REQUEST).forStatus(status);
        if(counts.rapid() >= rapidT)
            return new RuleViolation(RuleCode.RAPID_REQUEST, counts.rapid(), rapidT);

        int burstT = ruleProperties.get(RuleCode.BURST_REQUEST).forStatus(status);
        if(counts.burst() >= burstT)
            return new RuleViolation(RuleCode.BURST_REQUEST, counts.burst(), burstT);

        int multiT = ruleProperties.get(RuleCode.MULTI_OFFERING_BURST).forStatus(status);
        if(counts.offerings() >= multiT)
            return new RuleViolation(RuleCode.MULTI_OFFERING_BURST, counts.offerings(), multiT);

        return null;
    }


    private record RuleViolation(RuleCode rule, long observed, int threshold){}
}

package com.moneykk.moneytown.analysis.fds.command.application;

import com.moneykk.moneytown.analysis.fds.domain.DetectionType;
import com.moneykk.moneytown.analysis.fds.domain.EventType;
import com.moneykk.moneytown.analysis.fds.domain.FdsDetectionLog;
import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsDetectionLogRepository;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsUserStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FdsBlockApplier {

    private final FdsUserStateRepository fdsUserStateRepository;
    private final FdsDetectionLogRepository fdsDetectionLogRepository;

    @Transactional
    public void applyBlock(UUID userId, UUID requestId, UUID assetId,
                           RuleCode rule, long observed, int threshold){
        FdsUserState state = fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseGet(() -> fdsUserStateRepository.save(FdsUserState.create(userId)));

        fdsDetectionLogRepository.save(FdsDetectionLog.builder()
                .requestId(requestId)
                .userId(userId)
                .assetId(assetId)
                .detectionType(DetectionType.PRE)
                .eventType(EventType.SUBSCRIPTION_REQUEST)
                .ruleCode(rule)
                .observedValue((int)observed)
                .thresholdValue(threshold)
                .occurredAt(Instant.now())
                .build());

        state.block(rule.name());
    }
}

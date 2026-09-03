package com.moneykk.moneytown.analysis.fds.command.application;

import com.moneykk.moneytown.analysis.fds.domain.DetectionType;
import com.moneykk.moneytown.analysis.fds.domain.EventType;
import com.moneykk.moneytown.analysis.fds.domain.FdsDetectionLog;
import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import com.moneykk.moneytown.analysis.fds.domain.UserStatus;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsDetectionLogRepository;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsUserStateRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostFdsDetectionApplier {

    private final FdsDetectionLogRepository fdsDetectionLogRepository;
    private final FdsUserStateRepository fdsUserStateRepository;

    @Transactional
    public UserStatus apply(UUID userId, UUID eventId, UUID assetId, Instant occurredAt,
                            EventType eventType, RuleCode rule, int observed, int threshold){
        FdsUserState state = fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseGet(() -> fdsUserStateRepository.save(FdsUserState.create(userId)));

        fdsDetectionLogRepository.save(FdsDetectionLog.builder()
                .eventId(eventId)                 // requestId 는 null (POST)
                .userId(userId)
                .assetId(assetId)
                .detectionType(DetectionType.POST)
                .eventType(eventType)
                .ruleCode(rule)
                .observedValue(observed)
                .thresholdValue(threshold)
                .occurredAt(occurredAt)           // Instant.now() 아님 — 이벤트 발생 시각
                .build());

        switch (state.getStatus()){
            case NORMAL -> state.markSuspicious();
            case SUSPICIOUS -> state.block(rule.name());
            default -> {}
        }
        return state.getStatus();
    }
}

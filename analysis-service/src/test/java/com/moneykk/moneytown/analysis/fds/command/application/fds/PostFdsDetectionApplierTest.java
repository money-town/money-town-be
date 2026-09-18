package com.moneykk.moneytown.analysis.fds.command.application.fds;

import com.moneykk.moneytown.analysis.fds.command.application.PostFdsDetectionApplier;
import com.moneykk.moneytown.analysis.fds.domain.DetectionType;
import com.moneykk.moneytown.analysis.fds.domain.EventType;
import com.moneykk.moneytown.analysis.fds.domain.FdsDetectionLog;
import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import com.moneykk.moneytown.analysis.fds.domain.UserStatus;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsDetectionLogRepository;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsUserStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostFdsDetectionApplierTest {

    @Mock
    private FdsDetectionLogRepository fdsDetectionLogRepository;
    @Mock
    private FdsUserStateRepository fdsUserStateRepository;

    private PostFdsDetectionApplier applier;

    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private final Instant occurredAt = Instant.now().minusSeconds(30);

    @BeforeEach
    void setUp() {
        applier = new PostFdsDetectionApplier(fdsDetectionLogRepository, fdsUserStateRepository);
    }

    private UserStatus apply(FdsUserState state) {
        when(fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(state));
        return applier.apply(userId, eventId, assetId, occurredAt,
                EventType.SUBSCRIPTION_FAILED, RuleCode.REPEATED_FAILURE, 8, 8);
    }

    @Test
    @DisplayName("기존 FdsUserState가 있으면 그걸 그대로 쓰고 새로 저장하지 않는다")
    void apply_existingUserState_usesExistingAndDoesNotSaveNewState() {
        FdsUserState existing = FdsUserState.create(userId);

        apply(existing);

        verify(fdsUserStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("기존 FdsUserState가 없으면 새로 만들어서 저장한다")
    void apply_noExistingUserState_createsAndSavesNewState() {
        when(fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());
        when(fdsUserStateRepository.save(any(FdsUserState.class))).thenAnswer(inv -> inv.getArgument(0));

        UserStatus result = applier.apply(userId, eventId, assetId, occurredAt,
                EventType.SUBSCRIPTION_FAILED, RuleCode.REPEATED_FAILURE, 8, 8);

        ArgumentCaptor<FdsUserState> captor = ArgumentCaptor.forClass(FdsUserState.class);
        verify(fdsUserStateRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(result).isEqualTo(UserStatus.SUSPICIOUS);
    }

    @Test
    @DisplayName("POST 타입 탐지 로그를 eventId와 함께(requestId는 null) 저장한다")
    void apply_savesDetectionLogWithPostType() {
        apply(FdsUserState.create(userId));

        ArgumentCaptor<FdsDetectionLog> captor = ArgumentCaptor.forClass(FdsDetectionLog.class);
        verify(fdsDetectionLogRepository).save(captor.capture());
        FdsDetectionLog log = captor.getValue();
        assertThat(log.getDetectionType()).isEqualTo(DetectionType.POST);
        assertThat(log.getEventId()).isEqualTo(eventId);
        assertThat(log.getRequestId()).isNull();
        assertThat(log.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(log.getObservedValue()).isEqualTo(8);
        assertThat(log.getThresholdValue()).isEqualTo(8);
    }

    @Test
    @DisplayName("NORMAL 상태면 SUSPICIOUS로 전이된다")
    void apply_normalStatus_transitionsToSuspicious() {
        FdsUserState normal = FdsUserState.create(userId);

        UserStatus result = apply(normal);

        assertThat(result).isEqualTo(UserStatus.SUSPICIOUS);
        assertThat(normal.getStatus()).isEqualTo(UserStatus.SUSPICIOUS);
    }

    @Test
    @DisplayName("SUSPICIOUS 상태면 BLOCKED로 전이된다")
    void apply_suspiciousStatus_transitionsToBlocked() {
        FdsUserState suspicious = FdsUserState.create(userId);
        suspicious.markSuspicious();

        UserStatus result = apply(suspicious);

        assertThat(result).isEqualTo(UserStatus.BLOCKED);
        assertThat(suspicious.getStatus()).isEqualTo(UserStatus.BLOCKED);
    }

    @Test
    @DisplayName("이미 BLOCKED 상태면 예외 없이 BLOCKED를 그대로 유지한다")
    void apply_alreadyBlockedStatus_staysBlockedWithoutError() {
        FdsUserState blocked = FdsUserState.create(userId);
        blocked.block("PRIOR_VIOLATION");

        UserStatus result = apply(blocked);

        assertThat(result).isEqualTo(UserStatus.BLOCKED);
    }
}

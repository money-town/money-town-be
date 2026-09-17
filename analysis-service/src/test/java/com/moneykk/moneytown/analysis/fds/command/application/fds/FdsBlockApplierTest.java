package com.moneykk.moneytown.analysis.fds.command.application.fds;

import com.moneykk.moneytown.analysis.fds.command.application.FdsBlockApplier;
import com.moneykk.moneytown.analysis.fds.domain.DetectionType;
import com.moneykk.moneytown.analysis.fds.domain.FdsDetectionLog;
import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import com.moneykk.moneytown.analysis.fds.domain.UserStatus;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsDetectionLogRepository;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsUserStateRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FdsBlockApplierTest {

    @Mock
    private FdsUserStateRepository fdsUserStateRepository;
    @Mock
    private FdsDetectionLogRepository fdsDetectionLogRepository;

    private FdsBlockApplier applier;

    private final UUID userId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        applier = new FdsBlockApplier(fdsUserStateRepository, fdsDetectionLogRepository);
    }

    @Test
    @DisplayName("기존 FdsUserState가 있으면 그걸 그대로 쓰고 새로 저장하지 않는다")
    void applyBlock_existingUserState_usesExistingAndDoesNotSaveNewState() {
        FdsUserState existing = FdsUserState.create(userId);
        when(fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(existing));

        applier.applyBlock(userId, requestId, assetId, RuleCode.RAPID_REQUEST, 5, 5);

        verify(fdsUserStateRepository, never()).save(any());
        assertThat(existing.getStatus()).isEqualTo(UserStatus.BLOCKED);
    }

    @Test
    @DisplayName("기존 FdsUserState가 없으면 새로 만들어서 저장한다")
    void applyBlock_noExistingUserState_createsAndSavesNewState() {
        when(fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());
        when(fdsUserStateRepository.save(any(FdsUserState.class))).thenAnswer(inv -> inv.getArgument(0));

        applier.applyBlock(userId, requestId, assetId, RuleCode.RAPID_REQUEST, 5, 5);

        ArgumentCaptor<FdsUserState> captor = ArgumentCaptor.forClass(FdsUserState.class);
        verify(fdsUserStateRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.BLOCKED);
    }

    @Test
    @DisplayName("PRE 타입의 탐지 로그를 requestId와 함께 저장한다")
    void applyBlock_savesDetectionLogWithPreType() {
        FdsUserState existing = FdsUserState.create(userId);
        when(fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(existing));

        applier.applyBlock(userId, requestId, assetId, RuleCode.BURST_REQUEST, 20, 20);

        ArgumentCaptor<FdsDetectionLog> captor = ArgumentCaptor.forClass(FdsDetectionLog.class);
        verify(fdsDetectionLogRepository).save(captor.capture());
        FdsDetectionLog log = captor.getValue();
        assertThat(log.getDetectionType()).isEqualTo(DetectionType.PRE);
        assertThat(log.getRequestId()).isEqualTo(requestId);
        assertThat(log.getUserId()).isEqualTo(userId);
        assertThat(log.getAssetId()).isEqualTo(assetId);
        assertThat(log.getRuleCode()).isEqualTo(RuleCode.BURST_REQUEST);
        assertThat(log.getObservedValue()).isEqualTo(20);
        assertThat(log.getThresholdValue()).isEqualTo(20);
    }

    @Test
    @DisplayName("이미 BLOCKED 상태인 유저에게 다시 적용하면 BusinessException을 던진다")
    void applyBlock_alreadyBlockedState_throwsBusinessException() {
        FdsUserState blocked = FdsUserState.create(userId);
        blocked.block("PRIOR_VIOLATION");
        when(fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() ->
                applier.applyBlock(userId, requestId, assetId, RuleCode.RAPID_REQUEST, 5, 5))
                .isInstanceOf(BusinessException.class);
    }
}

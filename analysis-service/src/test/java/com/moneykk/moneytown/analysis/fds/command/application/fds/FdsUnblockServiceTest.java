package com.moneykk.moneytown.analysis.fds.command.application.fds;

import com.moneykk.moneytown.analysis.fds.command.application.FdsUnblockService;
import com.moneykk.moneytown.analysis.fds.command.dto.response.UnblockUserResult;
import com.moneykk.moneytown.analysis.fds.command.redis.FdsRedisCounter;
import com.moneykk.moneytown.analysis.fds.command.redis.PostFdsCounter;
import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.UserStatus;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsUserStateRepository;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FdsUnblockServiceTest {

    @Mock
    private FdsUserStateRepository fdsUserStateRepository;
    @Mock
    private FdsRedisCounter fdsRedisCounter;
    @Mock
    private PostFdsCounter postFdsCounter;

    private FdsUnblockService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FdsUnblockService(fdsUserStateRepository, fdsRedisCounter, postFdsCounter);
    }

    @Test
    @DisplayName("FDS 상태가 없으면 FDS_STATE_NOT_FOUND 예외를 던진다")
    void unblock_userNotFound_throwsStateNotFound() {
        when(fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unblock(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnalysisErrorCode.FDS_STATE_NOT_FOUND);
    }

    @Test
    @DisplayName("BLOCKED 상태면 해제하고 Redis 카운터를 정리한다")
    void unblock_blockedUser_unblocksAndClearsCounters() {
        FdsUserState state = FdsUserState.create(userId);
        state.block("RAPID_REQUEST");
        when(fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(state));
        when(fdsUserStateRepository.save(state)).thenReturn(state);

        UnblockUserResult result = service.unblock(userId);

        assertThat(result.status()).isEqualTo(UserStatus.NORMAL);
        assertThat(result.userId()).isEqualTo(userId);
        verify(fdsRedisCounter).clear(userId);
        verify(postFdsCounter).clear(userId);
    }

    @Test
    @DisplayName("BLOCKED 상태가 아니면 도메인 가드에 의해 예외가 나고 카운터는 정리하지 않는다")
    void unblock_notBlockedUser_throwsFromDomainGuardWithoutClearingCounters() {
        FdsUserState normal = FdsUserState.create(userId);
        when(fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(normal));

        assertThatThrownBy(() -> service.unblock(userId)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(fdsRedisCounter, postFdsCounter);
    }
}

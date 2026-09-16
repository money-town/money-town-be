package com.moneykk.moneytown.analysis.fds.domain;

import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FdsUserStateTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("create()로 만들면 NORMAL 상태로 시작한다")
    void create_startsAsNormal() {
        FdsUserState state = FdsUserState.create(userId);

        assertThat(state.getStatus()).isEqualTo(UserStatus.NORMAL);
        assertThat(state.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("NORMAL 상태에서 block하면 BLOCKED로 전이되고 사유/시각이 기록된다")
    void block_fromNormal_transitionsToBlocked() {
        FdsUserState state = FdsUserState.create(userId);

        state.block("RAPID_REQUEST");

        assertThat(state.getStatus()).isEqualTo(UserStatus.BLOCKED);
        assertThat(state.getBlockedReason()).isEqualTo("RAPID_REQUEST");
        assertThat(state.getBlockedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 BLOCKED면 다시 block할 수 없다")
    void block_alreadyBlocked_throws() {
        FdsUserState state = FdsUserState.create(userId);
        state.block("FIRST");

        assertThatThrownBy(() -> state.block("SECOND")).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("BLOCKED 상태에서 unblock하면 NORMAL로 돌아가고 관련 필드가 초기화된다")
    void unblock_fromBlocked_resetsToNormal() {
        FdsUserState state = FdsUserState.create(userId);
        state.block("RAPID_REQUEST");

        state.unblock();

        assertThat(state.getStatus()).isEqualTo(UserStatus.NORMAL);
        assertThat(state.getBlockedAt()).isNull();
        assertThat(state.getBlockedReason()).isNull();
        assertThat(state.getSuspiciousAt()).isNull();
    }

    @Test
    @DisplayName("BLOCKED가 아니면 unblock할 수 없다")
    void unblock_notBlocked_throws() {
        FdsUserState state = FdsUserState.create(userId);

        assertThatThrownBy(state::unblock).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("NORMAL 상태에서 markSuspicious하면 SUSPICIOUS로 전이된다")
    void markSuspicious_fromNormal_transitionsToSuspicious() {
        FdsUserState state = FdsUserState.create(userId);

        state.markSuspicious();

        assertThat(state.getStatus()).isEqualTo(UserStatus.SUSPICIOUS);
        assertThat(state.getSuspiciousAt()).isNotNull();
    }

    @Test
    @DisplayName("NORMAL이 아니면 markSuspicious할 수 없다")
    void markSuspicious_notNormal_throws() {
        FdsUserState state = FdsUserState.create(userId);
        state.markSuspicious();

        assertThatThrownBy(state::markSuspicious).isInstanceOf(BusinessException.class);
    }
}

package com.moneykk.moneytown.analysis.fds.command.dto;

import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UnblockUserResult(
        UUID userId,
        UserStatus status,
        Instant blockedAt,
        String blockReason,
        Instant updatedAt
) {

    public static UnblockUserResult from(FdsUserState state){
        return new UnblockUserResult(
                state.getUserId(),
                state.getStatus(),
                state.getBlockedAt(),
                state.getBlockedReason(),
                state.getUpdatedAt()
        );
    }
}

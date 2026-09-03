package com.moneykk.moneytown.analysis.fds.query.dto;

import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record FdsUserStateResponse(
        UUID userId,
        UserStatus status,
        Instant blockedAt,
        String blockedReason
) {
    public static FdsUserStateResponse from(FdsUserState s){
        return new FdsUserStateResponse(s.getUserId(), s.getStatus(), s.getBlockedAt(), s.getBlockedReason());
    }
}

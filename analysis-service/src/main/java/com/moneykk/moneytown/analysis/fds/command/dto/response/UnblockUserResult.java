package com.moneykk.moneytown.analysis.fds.command.dto.response;

import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record UnblockUserResult(
        @Schema(description = "사용자 ID")
        UUID userId,
        @Schema(description = "해제 후 FDS 상태", example = "NORMAL")
        UserStatus status,
        @Schema(description = "직전 차단 시각")
        Instant blockedAt,
        @Schema(description = "직전 차단 사유")
        String blockReason,
        @Schema(description = "상태 변경 시각")
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

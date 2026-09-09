package com.moneykk.moneytown.analysis.fds.query.dto;

import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record FdsUserStateResponse(
        @Schema(description = "사용자 ID")
        UUID userId,
        @Schema(description = "현재 FDS 상태 (NORMAL / SUSPICIOUS / BLOCKED)")
        UserStatus status,
        @Schema(description = "차단 시각 (미차단 시 null)")
        Instant blockedAt,
        @Schema(description = "차단 사유 (미차단 시 null)")
        String blockedReason
) {
    public static FdsUserStateResponse from(FdsUserState s){
        return new FdsUserStateResponse(s.getUserId(), s.getStatus(), s.getBlockedAt(), s.getBlockedReason());
    }
}

package com.moneykk.moneytown.user.dto.response;

import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.entity.type.AccountStatus;
import com.moneykk.moneytown.user.entity.type.KycStatus;
import com.moneykk.moneytown.user.entity.type.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "사용자 정보 응답")
public record UserResponse(
        @Schema(description = "사용자 ID", example = "3e05a4da-e1bb-4304-9e75-298aac529222")
        UUID userId,

        @Schema(description = "이메일", example = "hong@example.com")
        String email,
        String name,
        String phone,

        @Schema(description = "사용자 권한", example = "INVESTOR")
        UserRole userRole,

        @Schema(description = "계정 상태", example = "ACTIVE")
        AccountStatus accountStatus,
        KycStatus kycStatus,
        Instant kycExpiresAt,
        Instant createdAt
) {
    public static UserResponse from(User user, Instant now){
        KycStatus effectiveKycStatus = user.getKycStatus();

        if (user.getKycStatus() == KycStatus.VERIFIED
                && (user.getKycExpiresAt() == null
                || !now.isBefore(user.getKycExpiresAt()))) {
            effectiveKycStatus = KycStatus.EXPIRED;
        }

        return new UserResponse(
        user.getUserId(),
        user.getEmail(),
        user.getName(),
        user.getPhone(),
        user.getRole(),
        user.getAccountStatus(),
        effectiveKycStatus,
        user.getKycExpiresAt(),
        user.getCreatedAt());

    }

}

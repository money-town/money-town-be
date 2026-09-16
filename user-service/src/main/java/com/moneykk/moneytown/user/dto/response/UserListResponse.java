package com.moneykk.moneytown.user.dto.response;

import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.entity.type.AccountStatus;
import com.moneykk.moneytown.user.entity.type.KycStatus;
import com.moneykk.moneytown.user.entity.type.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserListResponse(
        UUID userId,
        String email,
        String name,
        String phone,
        UserRole role,
        AccountStatus accountStatus,
        KycStatus kycStatus,
        Instant createdAt

) {

    public static UserListResponse from(User user,Instant now) {
        KycStatus effectiveKycStatus = user.getKycStatus();

        if (user.getKycStatus() == KycStatus.VERIFIED
                && (user.getKycExpiresAt() == null
                || !now.isBefore(user.getKycExpiresAt()))) {
            effectiveKycStatus = KycStatus.EXPIRED;
        }

        return new UserListResponse(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getRole(),
                user.getAccountStatus(),
                effectiveKycStatus,
                user.getCreatedAt()
        );
    }
}

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

    public static UserListResponse from(User user) {
        return new UserListResponse(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getRole(),
                user.getAccountStatus(),
                user.getKycStatus(),
                user.getCreatedAt()
        );
    }
}

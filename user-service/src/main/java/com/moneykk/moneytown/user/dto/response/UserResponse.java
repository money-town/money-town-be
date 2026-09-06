package com.moneykk.moneytown.user.dto.response;

import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.entity.type.AccountStatus;
import com.moneykk.moneytown.user.entity.type.KycStatus;
import com.moneykk.moneytown.user.entity.type.UserRole;

import java.time.Instant;
import java.util.UUID;

// 단일 조회
public record UserResponse(
        UUID userId,
        String email,
        String name,
        String phone,
        UserRole userRole,
        AccountStatus accountStatus,
        KycStatus kycStatus,
        Instant kycExpiresAt,
        Instant createdAt
) {
    public static UserResponse from(User user){
        return new UserResponse(
        user.getUserId(),
        user.getEmail(),
        user.getName(),
        user.getPhone(),
        user.getRole(),
        user.getAccountStatus(),
        user.getKycStatus(),
        user.getKycExpiresAt(),
        user.getCreatedAt());

    }

}

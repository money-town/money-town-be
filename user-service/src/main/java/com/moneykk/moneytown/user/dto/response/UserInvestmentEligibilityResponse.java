package com.moneykk.moneytown.user.dto.response;

import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.entity.type.AccountStatus;
import com.moneykk.moneytown.user.entity.type.KycStatus;

import java.time.Instant;
import java.util.UUID;

public record UserInvestmentEligibilityResponse(
        UUID userId,
        String accountStatus,
        String kycStatus,
        Instant kycExpiresAt
){
    public static UserInvestmentEligibilityResponse from(
            User user
    ) {
        return new UserInvestmentEligibilityResponse(
                user.getUserId(),
                user.getAccountStatus().name(),
                user.getKycStatus().name(),
                user.getKycExpiresAt()
        );
    }



}

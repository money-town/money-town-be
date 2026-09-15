package com.moneykk.moneytown.user.dto.response;

import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.entity.type.AccountStatus;
import com.moneykk.moneytown.user.entity.type.KycStatus;

import java.time.Instant;
import java.util.UUID;

public record UserInvestmentEligibilityResponse(
        UUID userId,
        String userRole,
        String accountStatus,
        String kycStatus,
        Instant kycExpiresAt
){
    public static UserInvestmentEligibilityResponse from(
            User user,
            Instant now
    ) {
        KycStatus effectiveKycStatus = user.getKycStatus();

        if(user.getKycStatus() == KycStatus.VERIFIED &&
                (user.getKycExpiresAt() == null ||
                        !now.isBefore(user.getKycExpiresAt()))) {
            effectiveKycStatus = KycStatus.EXPIRED;
        }

        return new UserInvestmentEligibilityResponse(
                user.getUserId(),
                user.getRole().name(),
                user.getAccountStatus().name(),
                effectiveKycStatus.name(),
                user.getKycExpiresAt()
        );
    }



}

package com.moneykk.moneytown.wallet.client.dto;

import java.time.Instant;
import java.util.UUID;

// 정책 #12(9/2 확정): accountStatus=ACTIVE AND kycStatus=VERIFIED AND 현재시각 < kycExpiresAt
public record UserInvestmentEligibilityResponse(
        UUID userId,
        String accountStatus,
        String kycStatus,
        Instant kycExpiresAt
) {
    public boolean isEligibleForTransaction() {
        return "ACTIVE".equalsIgnoreCase(accountStatus)
                && "VERIFIED".equalsIgnoreCase(kycStatus)
                && kycExpiresAt != null
                && kycExpiresAt.isAfter(Instant.now());
    }
}

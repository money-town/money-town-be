package com.moneykk.moneytown.wallet.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

// accountStatus=ACTIVE AND kycStatus=VERIFIED AND 현재시각 < kycExpiresAt
// User 쪽이 필드를 추가해도(예: userRole) 깨지지 않도록 모르는 필드는 무시한다.
@JsonIgnoreProperties(ignoreUnknown = true)
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

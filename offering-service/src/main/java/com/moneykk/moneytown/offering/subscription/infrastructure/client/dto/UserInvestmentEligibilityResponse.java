package com.moneykk.moneytown.offering.subscription.infrastructure.client.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * User Service에서 조회한 청약 자격 판단용 최신 사용자 상태.
 *
 * 투자 가능 여부는 userRole, accountStatus, kycStatus와
 * KYC 만료 시각을 기준으로 Offering Service에서 판단한다.
 */
public record UserInvestmentEligibilityResponse(
        UUID userId,
        String userRole,
        String accountStatus,
        String kycStatus,
        Instant kycExpiresAt
) {

    public boolean isEligibleForSubscription(Instant now) {
        return now != null
                && "INVESTOR".equalsIgnoreCase(userRole)
                && "ACTIVE".equalsIgnoreCase(accountStatus)
                && "VERIFIED".equalsIgnoreCase(kycStatus)
                && kycExpiresAt != null
                && now.isBefore(kycExpiresAt);
    }

    public boolean isEligibleForOfferingManagement(Instant now) {
        return now != null
                && "ISSUER".equalsIgnoreCase(userRole)
                && "ACTIVE".equalsIgnoreCase(accountStatus)
                && "VERIFIED".equalsIgnoreCase(kycStatus)
                && kycExpiresAt != null
                && now.isBefore(kycExpiresAt);
    }
}
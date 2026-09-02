package com.moneykk.moneytown.offering.subscription.infrastructure.client.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * User Service에서 조회한 청약 자격 판단용 최신 사용자 상태.
 *
 * 투자 가능 여부는 investmentEligibilityStatus를 별도로 사용하지 않고
 * accountStatus와 kycStatus를 기준으로 Subscription Domain에서 판단한다.
 */
public record UserInvestmentEligibilityResponse(
        UUID userId,
        String accountStatus,
        String kycStatus,
        Instant kycExpiresAt
) {

    public boolean isEligibleForSubscription(Instant now) {
        return "ACTIVE".equalsIgnoreCase(accountStatus)
                && "VERIFIED".equalsIgnoreCase(kycStatus)
                && kycExpiresAt != null
                && now.isBefore(kycExpiresAt);
    }
}
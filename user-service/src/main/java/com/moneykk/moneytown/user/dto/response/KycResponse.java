package com.moneykk.moneytown.user.dto.response;

import com.moneykk.moneytown.user.entity.Kyc;
import com.moneykk.moneytown.user.entity.type.KycVerificationStatus;

import java.time.Instant;
import java.util.UUID;

public record KycResponse(UUID kycVerificationId,
                          UUID userId,
                          String occupationType,
                          String fundSource,
                          KycVerificationStatus status,
                          int attemptNo,
                          Instant submittedAt,
                          Instant reviewedAt,
                          Instant verifiedAt,
                          Instant expiresAt,
                          String rejectionReason) {

    public static KycResponse from(Kyc kyc) {
        return new KycResponse(
                kyc.getId(),
                kyc.getUserId(),
                kyc.getOccupationType(),
                kyc.getFundSource(),
                kyc.getStatus(),
                kyc.getAttemptNo(),
                kyc.getSubmittedAt(),
                kyc.getReviewedAt(),
                kyc.getVerifiedAt(),
                kyc.getExpiresAt(),
                kyc.getRejectionReason()
        );
    }
}

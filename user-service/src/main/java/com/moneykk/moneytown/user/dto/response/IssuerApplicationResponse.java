package com.moneykk.moneytown.user.dto.response;

import com.moneykk.moneytown.user.entity.IssuerApplication;
import com.moneykk.moneytown.user.entity.type.IssuerApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "발행자 권한 신청 응답")
public record IssuerApplicationResponse(
        UUID issuerApplicationId,
        UUID userId,
        String applicationReason,
        IssuerApplicationStatus status,
        Instant appliedAt,
        Instant reviewedAt,
        UUID reviewedBy,
        String rejectionReason
) {

    public static IssuerApplicationResponse from(IssuerApplication application) {
        return new IssuerApplicationResponse(
                application.getIssuerApplicationId(),
                application.getUserId(),
                application.getApplicationReason(),
                application.getStatus(),
                application.getAppliedAt(),
                application.getReviewedAt(),
                application.getReviewedBy(),
                application.getRejectionReason()
        );
    }
}

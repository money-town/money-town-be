package com.moneykk.moneytown.asset.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.Instant;
import java.util.UUID;

/** 공모 서비스가 성공 완료를 확정한 후 보내는 통지 */
public record OfferingCompletionRequest(
        @NotNull(message = "공모 ID는 필수입니다.") UUID offeringId,
        @NotNull(message = "공모 완료 시각은 필수입니다.")
        @PastOrPresent(message = "공모 완료 시각은 미래일 수 없습니다.") Instant completedAt
) {
}

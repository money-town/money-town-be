package com.moneykk.moneytown.asset.dto.request;

import com.moneykk.moneytown.asset.entity.RevenueSourceType;
import com.moneykk.moneytown.asset.entity.RevenueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** 멀티소스 수익 등록 요청 */
public record RevenueCreateRequest(

        @NotNull(message = "수익 출처 유형은 필수입니다.")
        RevenueSourceType sourceType,

        @NotBlank(message = "원본 참조 ID는 필수입니다.")
        @Size(max = 100, message = "원본 참조 ID는 100자 이하여야 합니다.")
        String sourceReferenceId,

        @NotNull(message = "수익 유형은 필수입니다.")
        RevenueType revenueType,

        @NotNull(message = "총수익은 필수입니다.")
        @Positive(message = "총수익은 0보다 커야 합니다.")
        BigDecimal grossAmount,

        @NotNull(message = "비용은 필수입니다.")
        @PositiveOrZero(message = "비용은 0 이상이어야 합니다.")
        BigDecimal expenseAmount,

        @NotNull(message = "수수료는 필수입니다.")
        @PositiveOrZero(message = "수수료는 0 이상이어야 합니다.")
        BigDecimal feeAmount,

        @NotBlank(message = "통화 코드는 필수입니다.")
        @Size(min = 3, max = 3, message = "통화 코드는 3자리여야 합니다.")
        String currency,

        @NotNull(message = "수익 기간 시작일은 필수입니다.")
        LocalDate periodStart,

        @NotNull(message = "수익 기간 종료일은 필수입니다.")
        LocalDate periodEnd,

        @NotNull(message = "원본 수익 데이터는 필수입니다.")
        Map<String, Object> rawPayload
) {
}
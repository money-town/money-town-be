package com.moneykk.moneytown.asset.dto.request;

import com.moneykk.moneytown.asset.entity.AssetType;
import com.moneykk.moneytown.asset.entity.OwnerBurdenPaymentMethod;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/** 자산 등록 요청 */
public record AssetCreateRequest(

        // 자산 이름
        @NotBlank(message = "자산명은 필수입니다.")
        @Size(max = 200, message = "자산명은 200자 이하여야 합니다.")
        String assetName,

        // 부동산 또는 음원 저작권
        @NotNull(message = "자산 유형은 필수입니다.")
        AssetType type,

        // 자산 설명
        @NotBlank(message = "자산 설명은 필수입니다.")
        String description,

        // 평가 금액(원)
        @NotNull(message = "자산 평가 금액은 필수입니다.")
        @Positive(message = "자산 평가 금액은 0보다 커야 합니다.")
        Long valuationAmount,

        // 예상 수익률(%)
        @NotNull(message = "예상 수익률은 필수입니다.")
        @Digits(
                integer = 3,
                fraction = 4,
                message = "예상 수익률은 정수 3자리, 소수 4자리까지 가능합니다."
        )
        BigDecimal expectedReturnRate,

        // 자산 유형별 상세 정보
        @NotNull(message = "자산 상세 정보는 필수입니다.")
        Map<String, Object> detailData,

        // 전체 지분 수량
        @NotNull(message = "전체 지분 수량은 필수입니다.")
        @Positive(message = "전체 지분 수량은 0보다 커야 합니다.")
        Long totalShareQuantity,

        // 매각대금 공제 또는 지갑 납부
        @NotNull(message = "소유주 차액 납부 방식은 필수입니다.")
        OwnerBurdenPaymentMethod ownerBurdenPaymentMethod
) {
}

package com.moneykk.moneytown.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "KYC 신청 요청")
public record KycApplyRequest(
        @Schema(description = "직업 유형", example = "회사원")
        @NotBlank(message = "직업 유형은 필수입니다.")
        @Size(max = 30, message = "직업 유형은 30자 이하여야 합니다.")
        String occupationType,


        @Schema(description = "자금 출처", example = "근로소득")
        @NotBlank(message = "자금 출처는 필수입니다.")
        @Size(max = 30, message = "자금 출처는 30자 이하여야 합니다.")
        String fundSource,


        @Schema(description = "약관 동의 버전", example = "v1.0")
        @NotBlank(message = "동의서 버전은 필수입니다.")
        @Size(max = 20, message = "동의서 버전은 20자 이하여야 합니다.")
        String consentVersion,


        @Schema(description = "국내 거주 여부", example = "true")
        @NotNull(message = "국내 거주 여부는 필수입니다.")
        Boolean domesticResident
) {
}

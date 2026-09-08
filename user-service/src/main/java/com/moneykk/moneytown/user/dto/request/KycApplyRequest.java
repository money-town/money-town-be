package com.moneykk.moneytown.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KycApplyRequest(
        @NotBlank(message = "직업 유형은 필수입니다.")
        @Size(max = 30, message = "직업 유형은 30자 이하여야 합니다.")
        String occupationType,

        @NotBlank(message = "자금 출처는 필수입니다.")
        @Size(max = 30, message = "자금 출처는 30자 이하여야 합니다.")
        String fundSource,

        @NotBlank(message = "동의서 버전은 필수입니다.")
        @Size(max = 20, message = "동의서 버전은 20자 이하여야 합니다.")
        String consentVersion,

        @NotNull(message = "국내 거주 여부는 필수입니다.")
        Boolean domesticResident
) {
}

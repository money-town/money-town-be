package com.moneykk.moneytown.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record KycApplyRequest(
        @NotBlank String occupationType,
        @NotBlank String fundSource,
        @NotBlank String consentVersion,
        @NotNull Boolean domesticResident
) {
}

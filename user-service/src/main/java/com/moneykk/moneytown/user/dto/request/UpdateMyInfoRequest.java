package com.moneykk.moneytown.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMyInfoRequest(

        @Size(max = 100)
        String name,

        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$")
        String phone
) {
}

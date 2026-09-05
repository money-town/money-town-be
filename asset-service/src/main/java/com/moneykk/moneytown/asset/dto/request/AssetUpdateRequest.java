package com.moneykk.moneytown.asset.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** 자산 정보 수정 요청 */
public record AssetUpdateRequest(

        // 수정할 자산명
        @Size(min = 2, max = 200,
                message = "자산명은 2자 이상 200자 이하여야 합니다.")
        @Pattern(regexp = "(?s).*\\S.*",
                message = "자산명은 공백일 수 없습니다.")
        String name,

        // 수정할 설명
        @Pattern(regexp = "(?s).*\\S.*",
                message = "자산 설명은 공백일 수 없습니다.")
        String description,

        // 소유주 이름
        @Size(max = 200,
                message = "소유주명은 200자 이하여야 합니다.")
        @Pattern(regexp = "(?s).*\\S.*",
                message = "소유주명은 공백일 수 없습니다.")
        String ownerName,

        // 주소 등 상세 정보. appraisalAmount는 수정 불가
        Map<String, Object> detail,

        // 수정할 전체 지분 수량
        @Positive(message = "전체 지분 수량은 1 이상이어야 합니다.")
        Long totalShareQuantity

) {
}

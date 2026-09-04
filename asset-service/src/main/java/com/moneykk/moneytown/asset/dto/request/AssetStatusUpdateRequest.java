package com.moneykk.moneytown.asset.dto.request;

import com.moneykk.moneytown.asset.entity.AssetStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 자산 상태 변경 요청 */
public record AssetStatusUpdateRequest(

        // 변경할 상태
        @NotNull(message = "변경할 자산 상태는 필수입니다.")
        AssetStatus status,

        // 반려 사유
        @Size(max = 500, message = "반려 사유는 500자 이하여야 합니다.")
        String rejectionReason

) {
}
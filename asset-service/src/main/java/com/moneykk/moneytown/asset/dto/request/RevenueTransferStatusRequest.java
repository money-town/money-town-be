package com.moneykk.moneytown.asset.dto.request;

import com.moneykk.moneytown.asset.entity.RevenueTransferStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 수익 전달 상태 변경 요청 */
public record RevenueTransferStatusRequest(

        @NotNull(message = "전달 상태는 필수입니다.")
        RevenueTransferStatus transferStatus,

        @Size(max = 500, message = "실패 사유는 500자 이하여야 합니다.")
        String failureReason
) {
}
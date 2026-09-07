package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.request.HoldingAdjustmentRequest;
import com.moneykk.moneytown.asset.service.HoldingCommandService;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 관리자 보유지분 관리 API */
@RestController
@RequestMapping("/api/v1/admin/holdings")
@RequiredArgsConstructor
public class AdminHoldingController {

    private final HoldingCommandService holdingCommandService;

    /**
     * 관리자 보유지분 수량 조정
     */
    // 도전 기능 활성화 시 @PostMapping("/{holdingId}/adjustments") 추가
    public ApiResponse<Void> adjustHolding(
            @PathVariable UUID holdingId,

            @RequestHeader(AuthHeaderConstants.USER_ID)
            UUID adminId,

            @RequestHeader(AuthHeaderConstants.USER_ROLE)
            String role,

            @Valid @RequestBody
            HoldingAdjustmentRequest request
    ) {
        // 보유지분 수량 조정
        holdingCommandService.adjust(
                holdingId,
                adminId,
                role,
                request
        );

        return ApiResponse.success(
                null,
                "보유지분 수량이 조정되었습니다."
        );
    }
}

package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.request.HoldingAllocationRequest;
import com.moneykk.moneytown.asset.dto.response.HoldingAllocationResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingSubscriptionStatusResponse;
import com.moneykk.moneytown.asset.service.HoldingCommandService;
import com.moneykk.moneytown.asset.service.HoldingQueryService;
import com.moneykk.moneytown.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 청약별 지분 처리 상태를 조회하는 내부 API */
@RestController
@RequestMapping("/internal/holdings")
@RequiredArgsConstructor
public class InternalHoldingController {

    private final HoldingQueryService holdingQueryService;
    private final HoldingCommandService holdingCommandService;

    @GetMapping("/subscriptions/{subscriptionId}")
    public ApiResponse<HoldingSubscriptionStatusResponse> getSubscriptionStatus(
            @PathVariable UUID subscriptionId
    ) {
        // 청약 지분 처리 상태 조회
        HoldingSubscriptionStatusResponse response =
                holdingQueryService.getSubscriptionStatus(subscriptionId);

        return ApiResponse.success(response, "청약 지분 처리 상태 조회가 완료되었습니다.");
    }

    /** 지분 배정 */
    @PostMapping("/allocations")
    public ApiResponse<HoldingAllocationResponse> allocate(
            @Valid @RequestBody HoldingAllocationRequest request
    ) {
        HoldingAllocationResponse response =
                holdingCommandService.allocate(request);

        return ApiResponse.success(
                response,
                "지분 배정이 완료되었습니다."
        );
    }
}

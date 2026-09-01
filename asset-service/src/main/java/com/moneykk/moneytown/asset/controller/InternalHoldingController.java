package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.response.HoldingSubscriptionStatusResponse;
import com.moneykk.moneytown.asset.service.HoldingQueryService;
import com.moneykk.moneytown.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 청약별 지분 처리 상태를 조회하는 내부 API */
@RestController
@RequestMapping("/internal/holdings")
@RequiredArgsConstructor
public class InternalHoldingController {

    private final HoldingQueryService holdingQueryService;

    @GetMapping("/subscriptions/{subscriptionId}")
    public ApiResponse<HoldingSubscriptionStatusResponse> getSubscriptionStatus(
            @PathVariable UUID subscriptionId
    ) {
        // 청약 지분 처리 상태 조회
        HoldingSubscriptionStatusResponse response =
                holdingQueryService.getSubscriptionStatus(subscriptionId);

        return ApiResponse.success(response, "청약 지분 처리 상태 조회가 완료되었습니다.");
    }
}

package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.response.RevenueDetailResponse;
import com.moneykk.moneytown.asset.service.RevenueQueryService;
import com.moneykk.moneytown.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 수익 조회 API */
@RestController
@RequestMapping("/assets/{assetId}/revenues")
@RequiredArgsConstructor
public class RevenueController {

    private final RevenueQueryService revenueQueryService;

    /** 정산 회차 개시 시 수익 금액 조회 */
    @GetMapping("/{revenueId}")
    public ApiResponse<RevenueDetailResponse> getRevenue(
            @PathVariable UUID assetId,
            @PathVariable UUID revenueId
    ) {
        // 자산에 등록된 수익 조회
        RevenueDetailResponse response =
                revenueQueryService.getRevenue(assetId, revenueId);

        // 조회 결과 반환
        return ApiResponse.success(
                response,
                "수익 조회가 완료되었습니다."
        );
    }
}
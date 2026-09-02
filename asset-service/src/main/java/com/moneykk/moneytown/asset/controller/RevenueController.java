package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.request.RevenueTransferStatusRequest;
import com.moneykk.moneytown.asset.dto.response.RevenueDetailResponse;
import com.moneykk.moneytown.asset.dto.response.RevenueTransferStatusResponse;
import com.moneykk.moneytown.asset.service.RevenueCommandService;
import com.moneykk.moneytown.asset.service.RevenueQueryService;
import com.moneykk.moneytown.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 수익 조회 및 상태 변경 API */
@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
public class RevenueController {

    private final RevenueQueryService revenueQueryService;
    private final RevenueCommandService revenueCommandService;

    /** 정산 회차 개시 시 수익 금액 조회 */
    @GetMapping("/{assetId}/revenues/{revenueId}")
    public ApiResponse<RevenueDetailResponse> getRevenue(
            @PathVariable UUID assetId,
            @PathVariable UUID revenueId
    ) {
        RevenueDetailResponse response =
                revenueQueryService.getRevenue(assetId, revenueId);

        return ApiResponse.success(
                response,
                "수익 조회가 완료되었습니다."
        );
    }

    /** 정산 서비스 전달 결과 반영 */
    @PatchMapping("/revenues/{revenueId}/transfer-status")
    public ApiResponse<RevenueTransferStatusResponse> updateTransferStatus(
            @PathVariable UUID revenueId,
            @Valid @RequestBody RevenueTransferStatusRequest request
    ) {
        RevenueTransferStatusResponse response =
                revenueCommandService.updateTransferStatus(revenueId, request);

        return ApiResponse.success(
                response,
                "수익 전달 상태가 변경되었습니다."
        );
    }
}
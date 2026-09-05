package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.response.HoldingHistoryListResponse;
import com.moneykk.moneytown.asset.service.HoldingQueryService;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 지분 변동 이력 조회 API */
@Validated
@RestController
@RequestMapping("/api/v1/holdings")
@RequiredArgsConstructor
public class HoldingHistoryController {

    private final HoldingQueryService holdingQueryService;

    /**
     * 특정 보유지분의 변동 이력 조회
     */
    @GetMapping("/{holdingId}/histories")
    public ApiResponse<HoldingHistoryListResponse> getHoldingHistories(
            @PathVariable UUID holdingId,

            @RequestHeader(AuthHeaderConstants.USER_ID)
            UUID userId,

            @RequestHeader(AuthHeaderConstants.USER_ROLE)
            String role,

            // 첫 페이지에서는 생략
            @RequestParam(required = false)
            UUID cursor,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
            int size,

            // 기본 등록일 내림차순
            @RequestParam(defaultValue = "DESC")
            Sort.Direction direction
    ) {
        HoldingHistoryListResponse response =
                holdingQueryService.getHoldingHistories(
                        holdingId,
                        userId,
                        role,
                        cursor,
                        size,
                        direction
                );

        return ApiResponse.success(
                response,
                "지분 변동 이력 조회가 완료되었습니다."
        );
    }
}
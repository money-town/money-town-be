package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.response.InternalRevenueListResponse;
import com.moneykk.moneytown.asset.service.RevenueQueryService;
import com.moneykk.moneytown.common.response.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 정산 서비스에서 사용하는 내부 수익 API */
@Validated
@RestController
@RequestMapping("/api/v1/internal/revenues")
@RequiredArgsConstructor
public class InternalRevenueController {

    private final RevenueQueryService revenueQueryService;

    /** 정산 서비스로 전달할 READY 상태 수익 목록 조회 */
    @GetMapping
    public ApiResponse<InternalRevenueListResponse> getReadyRevenues(
            @RequestParam(required = false) UUID cursor,

            @RequestParam(defaultValue = "100")
            @Min(value = 1, message = "조회 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "조회 크기는 100 이하여야 합니다.")
            int size,

            // 기본 정렬은 등록일 내림차순
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        InternalRevenueListResponse response =
                revenueQueryService.getReadyRevenues(cursor, size, direction);

        return ApiResponse.success(
                response,
                "전달 대기 수익 목록 조회가 완료되었습니다."
        );
    }
}
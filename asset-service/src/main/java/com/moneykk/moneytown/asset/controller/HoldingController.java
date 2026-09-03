package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.response.HoldingSnapshotResponse;
import com.moneykk.moneytown.asset.service.HoldingQueryService;
import com.moneykk.moneytown.common.response.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** 보유지분 조회 API */
@Validated
@RestController
@RequestMapping("/api/v1/assets/{assetId}/holdings")
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingQueryService holdingQueryService;

    /** 배당 기준일의 보유지분 조회 */
    @GetMapping
    public ApiResponse<HoldingSnapshotResponse> getSnapshot(
            @PathVariable UUID assetId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate asOf,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "100")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
            int size
    ) {
        // 기준일 지분 스냅샷 조회
        HoldingSnapshotResponse response =
                holdingQueryService.getSnapshot(assetId, asOf, cursor, size);

        return ApiResponse.success(
                response,
                "기준일 보유지분 조회가 완료되었습니다."
        );
    }
}

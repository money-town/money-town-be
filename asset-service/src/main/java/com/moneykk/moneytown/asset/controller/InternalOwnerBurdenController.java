package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.request.OfferingCompletionRequest;
import com.moneykk.moneytown.asset.dto.response.OwnerBurdenQuoteResponse;
import com.moneykk.moneytown.asset.service.OwnerBurdenService;
import com.moneykk.moneytown.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/assets")
public class InternalOwnerBurdenController {
    private final OwnerBurdenService ownerBurdenService;

    /** 공모 성공 완료 시에만 호출 */
    @PostMapping("/{assetId}/offering-completion")
    public ApiResponse<Void> recordOfferingCompletion(
            @PathVariable UUID assetId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody OfferingCompletionRequest request
    ) {
        ownerBurdenService.recordOfferingCompletion(assetId, role, request);
        return ApiResponse.success(null, "공모 완료 시각과 소유주 부담금이 확정되었습니다.");
    }

    /** 지갑 납부 또는 매각대금 공제 전 견적 조회 */
    @GetMapping("/{assetId}/owner-burden/quote")
    public ApiResponse<OwnerBurdenQuoteResponse> getQuote(
            @PathVariable UUID assetId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
    ) {
        return ApiResponse.success(ownerBurdenService.getQuote(assetId, role, asOf),
                "소유주 부담금 견적 조회가 완료되었습니다.");
    }
}

package com.moneykk.moneytown.offering.offering.query.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.offering.query.application.OfferingQueryService;
import com.moneykk.moneytown.offering.offering.query.dto.response.OfferingDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/offerings")
public class OfferingQueryController {

    private final OfferingQueryService offeringQueryService;

    // TODO: Gateway 인증/인가 정책 확정 후 사용자 정보 전달 방식 재검토
    @GetMapping("/{offeringId}")
    public ResponseEntity<ApiResponse<OfferingDetailResponse>> getOffering(
            @PathVariable UUID offeringId,
            @RequestHeader(
                    value = "X-User-Id",
                    required = false
            ) UUID userId,
            @RequestHeader(
                    value = "X-User-Role",
                    required = false
            ) String role
    ) {
        OfferingDetailResponse response =
                offeringQueryService.getOffering(
                        offeringId,
                        userId,
                        role
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        response,
                        "공모 상품 상세 조회가 완료되었습니다."
                )
        );
    }
}
package com.moneykk.moneytown.offering.offering.command.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCommandService;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingCreateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCreateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/offerings")
public class OfferingCommandController {

    private final OfferingCommandService offeringCommandService;

    // TODO: Gateway/서비스 인가 정책 확정 후 ISSUER 권한 검증 적용
    @PostMapping
    public ResponseEntity<ApiResponse<OfferingCreateResponse>> createOffering(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody OfferingCreateRequest request
    ) {
        OfferingCreateResponse response =
                offeringCommandService.create(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        response,
                        "공모 상품 등록이 완료되었습니다."
                ));
    }
}
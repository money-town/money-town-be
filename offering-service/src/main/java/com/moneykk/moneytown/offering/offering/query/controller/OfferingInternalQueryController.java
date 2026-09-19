package com.moneykk.moneytown.offering.offering.query.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.query.application.OfferingQueryService;
import com.moneykk.moneytown.offering.offering.query.dto.response.AiPortfolioCandidateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/offerings")
@Tag(
        name = "공모 내부 조회",
        description = "서비스 간 호출을 위한 공모 내부 조회 API"
)
public class OfferingInternalQueryController {

    private static final String SYSTEM_ROLE = "SYSTEM";

    private final OfferingQueryService offeringQueryService;

    @Operation(
            summary = "AI 포트폴리오 공모 후보 조회",
            description = "AI 포트폴리오 생성에 사용할 진행 중인 공모 후보를 조회합니다."
    )
    @GetMapping("/ai-portfolio-candidates")
    public ResponseEntity<
            ApiResponse<List<AiPortfolioCandidateResponse>>
            > getAiPortfolioCandidates(
            @RequestHeader(
                    value = AuthHeaderConstants.USER_ROLE,
                    required = false
            )
            String role,
            @RequestParam(defaultValue = "10")
            @Min(1)
            @Max(20)
            int limit
    ) {
        validateSystemRole(role);

        List<AiPortfolioCandidateResponse> response =
                offeringQueryService.getAiPortfolioCandidates(limit);

        String message = response.isEmpty()
                ? "조회된 AI 포트폴리오 공모 후보가 없습니다."
                : "AI 포트폴리오 공모 후보 조회가 완료되었습니다.";

        return ResponseEntity.ok(
                ApiResponse.success(
                        response,
                        message
                )
        );
    }

    private void validateSystemRole(String role) {
        if (!SYSTEM_ROLE.equals(role)) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ACCESS_DENIED
            );
        }
    }
}

package com.moneykk.moneytown.analysis.ai.query.controller;

import com.moneykk.moneytown.analysis.ai.query.application.PortfolioQueryService;
import com.moneykk.moneytown.analysis.ai.query.dto.PortfolioDetailResponse;
import com.moneykk.moneytown.analysis.ai.query.dto.PortfolioItemResponse;
import com.moneykk.moneytown.analysis.ai.query.dto.PortfolioSearchCondition;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;


@Tag(name = "AI", description = "AI 포트폴리오 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analysis/ai/portfolios")
public class PortfolioQueryController {

    private final PortfolioQueryService portfolioQueryService;

    @Operation(
            summary = "AI 포트폴리오 목록 조회 (관리자)",
            description = "ADMIN이 전체 포트폴리오를 조건·페이지로 조회합니다."
    )
    @GetMapping
    public ApiResponse<PageResponse<PortfolioItemResponse>> getPortfolios(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            PortfolioSearchCondition searchCondition,
            Pageable pageable
    ){
        if(!"ADMIN".equals(role)){
            throw new BusinessException(AnalysisErrorCode.AI_FORBIDDEN);
        }

        return ApiResponse.success(portfolioQueryService.getPortfolios(searchCondition, pageable), "포트폴리오 목록을 조회했습니다.");
    }

    @Operation(
            summary = "내 AI 포트폴리오 목록 조회",
            description = "로그인한 사용자 본인의 포트폴리오를 조건·페이지로 조회합니다."
    )
    @GetMapping("/me")
    public ApiResponse<PageResponse<PortfolioItemResponse>> getMyPortfolios(
            @RequestHeader(value = "X-User-Id") UUID userId,
            PortfolioSearchCondition searchCondition,
            Pageable pageable
    ){
        return ApiResponse.success(portfolioQueryService.getMyPortfolios(userId, searchCondition, pageable), "내 포트폴리오 목록을 조회했습니다.");
    }

    @Operation(
            summary = "AI 포트폴리오 상세 조회",
            description = "포트폴리오 단건을 조회합니다. 본인 또는 ADMIN만 가능합니다."
    )
    @GetMapping("/{portfolioId}")
    public ApiResponse<PortfolioDetailResponse> getPortfolio(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id") UUID userId,
            @PathVariable UUID portfolioId
    ){
        if(role == null){
            throw new BusinessException(AnalysisErrorCode.AI_FORBIDDEN);
        }

        return ApiResponse.success(portfolioQueryService.getPortfolio(role, userId, portfolioId), "포트폴리오를 조회했습니다.");
    }
}

package com.moneykk.moneytown.analysis.ai.command.controller;

import com.moneykk.moneytown.analysis.ai.command.application.PortFolioCommandService;
import com.moneykk.moneytown.analysis.ai.command.dto.CreatePortfolioRequest;
import com.moneykk.moneytown.analysis.ai.command.dto.CreatePortfolioResponse;
import com.moneykk.moneytown.analysis.ai.command.dto.DeletePortfolioResponse;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analysis/ai/portfolios")
public class PortfolioCommandController {

    private final PortFolioCommandService portFolioCommandService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreatePortfolioResponse>> createPortfolio(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @Valid @RequestBody CreatePortfolioRequest request
    ){

        if(!"INVESTOR".equals(role)){
            throw new BusinessException(AnalysisErrorCode.AI_FORBIDDEN);
        }
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(portFolioCommandService.createPortfolio(userId, idempotencyKey, request),
                                "AI 포트폴리오 생성 요청이 접수되었습니다."))
                ;
    }

    @DeleteMapping("/{portfolioId}")
    public ApiResponse<DeletePortfolioResponse> deletePortfolio(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @PathVariable UUID portfolioId
    ){
        return ApiResponse.success(portFolioCommandService.deletePortfolio(role, userId, portfolioId), "AI 포트폴리오가 삭제되었습니다.");
    }
}

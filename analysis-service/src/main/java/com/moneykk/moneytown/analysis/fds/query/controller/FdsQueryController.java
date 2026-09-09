package com.moneykk.moneytown.analysis.fds.query.controller;

import com.moneykk.moneytown.analysis.fds.query.application.FdsQueryService;
import com.moneykk.moneytown.analysis.fds.query.dto.FdsDetectionLogResponse;
import com.moneykk.moneytown.analysis.fds.query.dto.FdsDetectionLogSearchCondition;
import com.moneykk.moneytown.analysis.fds.query.dto.FdsUserStateResponse;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "FDS", description = "FDS 사용자 상태 및 탐지 로그 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analysis/fds")
public class FdsQueryController {

    private final FdsQueryService fdsQueryService;


    @Operation(
            summary = "FDS 사용자 상태 조회 (관리자)",
            description = "ADMIN이 특정 사용자의 FDS 차단 상태를 조회합니다."
    )
    @GetMapping("/users/{userId}")
    public ApiResponse<FdsUserStateResponse> getUserState(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID userId
    ){
        if(!"ADMIN".equals(role)){
            throw new BusinessException(AnalysisErrorCode.FDS_FORBIDDEN);
        }

        return ApiResponse.success(fdsQueryService.getUserState(userId), "FDS 사용자 상태를 조회했습니다.");
    }

    @Operation(
            summary = "FDS 탐지 로그 조회 (관리자)",
            description = "ADMIN이 FDS 탐지 이력을 조건·페이지로 조회합니다."
    )
    @GetMapping("/detections")
    public ApiResponse<PageResponse<FdsDetectionLogResponse>> getDetectionLogs(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            FdsDetectionLogSearchCondition searchCondition,
            Pageable pageable
    ){
        if(!"ADMIN".equals(role)){
            throw new BusinessException(AnalysisErrorCode.FDS_FORBIDDEN);
        }

        return ApiResponse.success(fdsQueryService.getDetectionLogs(searchCondition, pageable), "FDS 탐지 로그를 조회했습니다.");
    }
}

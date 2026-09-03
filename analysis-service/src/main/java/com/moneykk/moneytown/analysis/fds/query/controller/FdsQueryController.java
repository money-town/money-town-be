package com.moneykk.moneytown.analysis.fds.query.controller;

import com.moneykk.moneytown.analysis.fds.query.application.FdsQueryService;
import com.moneykk.moneytown.analysis.fds.query.dto.FdsDetectionLogResponse;
import com.moneykk.moneytown.analysis.fds.query.dto.FdsDetectionLogSearchCondition;
import com.moneykk.moneytown.analysis.fds.query.dto.FdsUserStateResponse;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analysis/fds")
public class FdsQueryController {

    private final FdsQueryService fdsQueryService;


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

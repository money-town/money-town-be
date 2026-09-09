package com.moneykk.moneytown.analysis.fds.command.controller;

import com.moneykk.moneytown.analysis.fds.command.application.FdsUnblockService;
import com.moneykk.moneytown.analysis.fds.command.dto.response.UnblockUserResult;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "FDS", description = "FDS 사용자 상태 변경 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class FdsUserStateCommandController {

    private final FdsUnblockService fdsUnblockService;

    @Operation(
            summary = "FDS 차단 해제 (관리자)",
            description = "ADMIN이 차단된 사용자의 FDS 상태를 정상으로 되돌립니다."
    )
    @PatchMapping("/analysis/fds/users/{userId}/unblock")
    public ApiResponse<UnblockUserResult> unblock(
            @PathVariable UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ){
        if(!"ADMIN".equals(role)){
            throw new BusinessException(AnalysisErrorCode.FDS_FORBIDDEN);
        }

        return ApiResponse.success(fdsUnblockService.unblock(userId), "차단을 해제했습니다.");
    }
}

package com.moneykk.moneytown.analysis.fds.command.controller;

import com.moneykk.moneytown.analysis.fds.command.application.FdsUnblockService;
import com.moneykk.moneytown.analysis.fds.command.dto.response.UnblockUserResult;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FdsUserStateCommandController {

    private final FdsUnblockService fdsUnblockService;

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

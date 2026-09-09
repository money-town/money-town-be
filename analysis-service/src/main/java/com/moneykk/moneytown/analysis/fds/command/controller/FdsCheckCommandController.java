package com.moneykk.moneytown.analysis.fds.command.controller;

import com.moneykk.moneytown.analysis.fds.command.application.PreFdsCheckService;
import com.moneykk.moneytown.analysis.fds.command.dto.request.PreFdsCheckRequest;
import com.moneykk.moneytown.analysis.fds.command.dto.response.PreFdsCheckResult;
import com.moneykk.moneytown.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "FDS (내부)", description = "서비스 간 내부 FDS 검사 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class FdsCheckCommandController {

    private final PreFdsCheckService preFdsCheckService;

    @Operation(
            summary = "사전 FDS 검사 (내부)",
            description = "청약/거래 전 FDS 규칙을 검사하여 PASS 또는 BLOCK을 반환합니다."
    )
    @PostMapping("/internal/fds/check")
    public ApiResponse<PreFdsCheckResult> check(@Valid @RequestBody PreFdsCheckRequest request){
        return ApiResponse.success(preFdsCheckService.check(request), "FDS 검사를 완료했습니다.");
    }
}

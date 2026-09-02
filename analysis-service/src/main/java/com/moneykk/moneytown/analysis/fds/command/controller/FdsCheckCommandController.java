package com.moneykk.moneytown.analysis.fds.command.controller;

import com.moneykk.moneytown.analysis.fds.command.application.PreFdsCheckService;
import com.moneykk.moneytown.analysis.fds.command.dto.request.PreFdsCheckRequest;
import com.moneykk.moneytown.analysis.fds.command.dto.response.PreFdsCheckResult;
import com.moneykk.moneytown.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class FdsCheckCommandController {

    private final PreFdsCheckService preFdsCheckService;

    @PostMapping("/internal/fds/check")
    public ApiResponse<PreFdsCheckResult> check(@Valid @RequestBody PreFdsCheckRequest request){
        return ApiResponse.success(preFdsCheckService.check(request), "FDS 검사를 완료했습니다.");
    }
}

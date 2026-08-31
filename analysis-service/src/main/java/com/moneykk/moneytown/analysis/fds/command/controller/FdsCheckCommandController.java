package com.moneykk.moneytown.analysis.fds.command.controller;

import com.moneykk.moneytown.analysis.fds.command.application.PreFdsCheckService;
import com.moneykk.moneytown.analysis.fds.command.dto.PreFdsCheckRequest;
import com.moneykk.moneytown.analysis.fds.command.dto.PreFdsCheckResult;
import com.moneykk.moneytown.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FdsCheckCommandController {

    private final PreFdsCheckService preFdsCheckService;

    @PostMapping("/internal/fds/check")
    public ApiResponse<PreFdsCheckResult> check(@Valid @RequestBody PreFdsCheckRequest request){
        return ApiResponse.success(preFdsCheckService.check(request), "FDS 검사를 완료했습니다.");
    }
}

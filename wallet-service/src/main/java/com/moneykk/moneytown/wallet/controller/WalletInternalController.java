package com.moneykk.moneytown.wallet.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.wallet.dto.response.WalletStatusResponse;
import com.moneykk.moneytown.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// User 서비스가 회원 탈퇴 처리 전 호출하는 내부 전용 API.
// 탈퇴 차단 여부 판단은 User가 하고, Wallet은 판단에 필요한 사실(잔액/동결 상태)만 제공한다.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/wallets")
public class WalletInternalController {

    private final WalletService walletService;

    //TODO: 인가 코드 추가 (User 서비스 전용 내부 호출임을 검증)
    @GetMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<WalletStatusResponse>> getWalletStatus(
            @PathVariable UUID userId
    ) {
        WalletStatusResponse response = walletService.getWalletStatus(userId);

        return ResponseEntity.ok(
                ApiResponse.success(response, "지갑 상태 조회가 완료되었습니다.")
        );
    }
}

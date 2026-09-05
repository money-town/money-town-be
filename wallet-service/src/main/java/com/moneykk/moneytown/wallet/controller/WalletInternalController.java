package com.moneykk.moneytown.wallet.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.wallet.dto.response.WalletStatusResponse;
import com.moneykk.moneytown.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// User 서비스가 회원 탈퇴 처리 전 호출하는 내부 전용 API.
// 탈퇴 차단 여부 판단은 User가 하고, Wallet은 판단에 필요한 사실(잔액/동결 상태)만 제공한다.
@Tag(name = "Wallet Internal", description = "다른 서비스가 서비스 간 호출로만 사용하는 내부 전용 API. 인증/인가 미구현 (TODO)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/wallets")
public class WalletInternalController {

    private final WalletService walletService;

    //TODO: 인가 코드 추가 (User 서비스 전용 내부 호출임을 검증)
    @Operation(
            summary = "지갑 상태 조회 (탈퇴 전 확인용)",
            description = "User 서비스가 회원 탈퇴 처리 전에 호출한다. hasActiveHold가 true면 청약금이 동결 중이라는 뜻이고, "
                    + "탈퇴를 막을지 여부는 User 서비스가 판단한다. Wallet은 판단에 필요한 사실만 제공한다."
    )
    @GetMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<WalletStatusResponse>> getWalletStatus(
            @Parameter(description = "탈퇴 대상 사용자 ID") @PathVariable UUID userId
    ) {
        WalletStatusResponse response = walletService.getWalletStatus(userId);

        return ResponseEntity.ok(
                ApiResponse.success(response, "지갑 상태 조회가 완료되었습니다.")
        );
    }
}

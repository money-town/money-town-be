package com.moneykk.moneytown.wallet.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.wallet.dto.response.WalletHoldStatusResponse;
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

// Offering(Subscription 도메인)이 관리자 재처리·보상 처리 시 호출하는 내부 전용 조회 API.
// 상태 변경은 Kafka 이벤트 기반 그대로 유지, 이 API는 순수 조회 전용.
@Tag(name = "Wallet Internal", description = "다른 서비스가 서비스 간 호출로만 사용하는 내부 전용 API. 인증/인가 미구현 (TODO)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/wallet-holds")
public class WalletInternalHoldController {

    private final WalletService walletService;

    //TODO: 인가 코드 추가 (Offering 서비스 전용 내부 호출임을 검증)
    @Operation(
            summary = "청약별 Wallet Hold 상태 조회",
            description = "subscriptionId 기준으로 현재 Wallet의 동결 처리 상태(HELD/RELEASED/COMMITTED/REFUNDED)와 금액을 조회한다. "
                    + "처리 이력이 없으면 404(WALLET_404_02)를 반환한다."
    )
    @GetMapping("/{subscriptionId}")
    public ResponseEntity<ApiResponse<WalletHoldStatusResponse>> getWalletHoldStatus(
            @Parameter(description = "조회할 청약 ID") @PathVariable UUID subscriptionId
    ) {
        WalletHoldStatusResponse response = walletService.getWalletHoldStatus(subscriptionId);

        return ResponseEntity.ok(
                ApiResponse.success(response, "청약금 처리 상태 조회가 완료되었습니다.")
        );
    }
}

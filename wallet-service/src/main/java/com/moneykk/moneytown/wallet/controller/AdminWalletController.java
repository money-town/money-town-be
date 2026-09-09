package com.moneykk.moneytown.wallet.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.wallet.dto.response.AdminWalletDetailResponse;
import com.moneykk.moneytown.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Wallet Admin", description = "운영자(ADMIN) 전용 지갑 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/wallets")
public class AdminWalletController {

    private final WalletService walletService;

    @Operation(
            summary = "지갑 상세 조회",
            description = "운영자가 특정 지갑의 상세 정보(잔액/동결/가용잔액/삭제여부 등)를 조회한다. "
                    + "ADMIN 권한이 아니면 403(WALLET_403_02)을 반환한다."
    )
    @GetMapping("/{walletId}")
    public ResponseEntity<ApiResponse<AdminWalletDetailResponse>> getWalletDetail(
            @Parameter(description = "조회할 지갑 ID") @PathVariable Long walletId,
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role
    ) {
        AdminWalletDetailResponse response = walletService.getWalletDetail(walletId, role);

        return ResponseEntity.ok(
                ApiResponse.success(response, "지갑 상세 조회가 완료되었습니다.")
        );
    }
}

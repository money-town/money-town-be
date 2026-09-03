package com.moneykk.moneytown.wallet.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.wallet.dto.request.TransactionRequest;
import com.moneykk.moneytown.wallet.dto.response.TransactionListItemResponse;
import com.moneykk.moneytown.wallet.dto.response.TransactionResponse;
import com.moneykk.moneytown.wallet.dto.response.WalletResponse;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId
    ) {
        WalletResponse response = walletService.getMyWallet(userId);

        return ResponseEntity.ok(
                ApiResponse.success(response, "지갑 조회가 완료되었습니다.")
        );
    }

    @GetMapping("/me/transactions")
    public ResponseEntity<ApiResponse<PageResponse<TransactionListItemResponse>>> getTransactions(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestParam(required = false) WalletTransactionType type,
            Pageable pageable
    ) {
        PageResponse<TransactionListItemResponse> response = walletService.getTransactions(userId, type, pageable);

        return ResponseEntity.ok(
                ApiResponse.success(response, "거래 내역 조회가 완료되었습니다.")
        );
    }

    @PostMapping("/me/deposits")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransactionRequest request
    ) {
        TransactionResponse response = walletService.deposit(userId, idempotencyKey, request.amount());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, "충전 처리가 완료되었습니다.")
        );
    }

    @PostMapping("/me/withdrawals")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransactionRequest request
    ) {
        TransactionResponse response = walletService.withdraw(userId, idempotencyKey, request.amount());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, "출금 처리가 완료되었습니다.")
        );
    }
}

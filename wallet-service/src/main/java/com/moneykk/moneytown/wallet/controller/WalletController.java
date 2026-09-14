package com.moneykk.moneytown.wallet.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.wallet.dto.request.TransactionRequest;
import com.moneykk.moneytown.wallet.dto.response.CursorPageResponse;
import com.moneykk.moneytown.wallet.dto.response.TransactionListItemResponse;
import com.moneykk.moneytown.wallet.dto.response.TransactionResponse;
import com.moneykk.moneytown.wallet.dto.response.WalletResponse;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Tag(name = "Wallet", description = "내 지갑 조회 및 입출금 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;

    @Operation(
            summary = "내 지갑 조회",
            description = "로그인한 사용자의 지갑 잔액(총액/동결/가용)을 조회한다. 지갑이 없으면 404(W404)를 반환한다."
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId
    ) {
        WalletResponse response = walletService.getMyWallet(userId);

        return ResponseEntity.ok(
                ApiResponse.success(response, "지갑 조회가 완료되었습니다.")
        );
    }

    @Operation(
            summary = "거래 내역 조회",
            description = "내 지갑의 거래 내역을 최신순으로 커서 기반 조회한다. type/from/to는 생략 가능하고, "
                    + "cursor를 생략하면 최신 거래부터 조회한다. 응답의 nextCursor를 다음 요청의 cursor로 그대로 넘기면 이어서 조회된다."
    )
    @GetMapping("/me/transactions")
    public ResponseEntity<ApiResponse<CursorPageResponse<TransactionListItemResponse>>> getTransactions(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @Parameter(description = "거래 타입 필터 (생략 시 전체 조회)") @RequestParam(required = false) WalletTransactionType type,
            @Parameter(description = "조회 시작 시각, 이 시각 이후 거래만 조회 (생략 가능)") @RequestParam(required = false) Instant from,
            @Parameter(description = "조회 종료 시각, 이 시각 이전 거래만 조회 (생략 가능)") @RequestParam(required = false) Instant to,
            @Parameter(description = "이전 응답의 nextCursor (생략하면 최신 거래부터 조회)") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    ) {
        CursorPageResponse<TransactionListItemResponse> response =
                walletService.getTransactions(userId, type, from, to, cursor, size);

        return ResponseEntity.ok(
                ApiResponse.success(response, "거래 내역 조회가 완료되었습니다.")
        );
    }

    @Operation(
            summary = "예치금 충전",
            description = "지갑에 예치금을 충전한다. 동일한 Idempotency-Key로 같은 요청이 재전송되면 기존 결과를 그대로 반환하고, "
                    + "같은 키에 다른 내용(타입/금액)의 요청이 오면 409(WALLET_409_01)를 반환한다."
    )
    @PostMapping("/me/deposits")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @Parameter(description = "요청 재시도를 식별하는 클라이언트 생성 키", required = true, example = "3f6b6c6e-2b8e-4e2a-9c33-1a2b3c4d5e6f")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransactionRequest request
    ) {
        TransactionResponse response = walletService.deposit(userId, idempotencyKey, request.amount());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, "충전 처리가 완료되었습니다.")
        );
    }

    @Operation(
            summary = "예치금 출금",
            description = "지갑에서 예치금을 출금한다. 가용잔액이 부족하면 400(W001)을 반환한다. "
                    + "Idempotency-Key 처리 방식은 충전 API와 동일하다."
    )
    @PostMapping("/me/withdrawals")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @Parameter(description = "요청 재시도를 식별하는 클라이언트 생성 키", required = true, example = "3f6b6c6e-2b8e-4e2a-9c33-1a2b3c4d5e6f")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransactionRequest request
    ) {
        TransactionResponse response = walletService.withdraw(userId, idempotencyKey, request.amount());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, "출금 처리가 완료되었습니다.")
        );
    }
}

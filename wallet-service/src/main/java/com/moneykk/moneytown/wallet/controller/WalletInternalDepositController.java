package com.moneykk.moneytown.wallet.controller;

// Settlement 서비스가 배당/자산종료 정산 지급 시 호출하는 내부 전용 API.
// /api/v1/internal/{dividends,settlements}로 각각 독립된 리소스다.
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.wallet.dto.request.DividendDepositRequest;
import com.moneykk.moneytown.wallet.dto.request.SettlementDepositRequest;
import com.moneykk.moneytown.wallet.dto.response.DividendDepositResponse;
import com.moneykk.moneytown.wallet.dto.response.SettlementDepositResponse;
import com.moneykk.moneytown.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Wallet Internal Deposit", description = "Settlement 서비스가 배당/자산종료 정산 지급 시 호출하는 내부 전용 API. 인증/인가 미구현 (TODO)")
//TODO: 인가 코드 추가 (Settlement 서비스 전용 내부 호출임을 검증)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal")
public class WalletInternalDepositController {

    private final WalletService walletService;

    @Operation(
            summary = "배당금 입금",
            description = "Settlement 서비스가 배당 지급 시 호출한다. 시스템 간 호출이라 KYC/거래가능상태 체크는 하지 않는다. "
                    + "동일한 idempotencyKey로 같은 요청이 재전송되면 기존 결과를 그대로 반환하고, 같은 키에 다른 내용이면 409(WALLET_409_01)를 반환한다."
    )
    @PostMapping("/dividends")
    public ResponseEntity<ApiResponse<DividendDepositResponse>> depositDividend(
            @Valid @RequestBody DividendDepositRequest request
    ) {
        DividendDepositResponse response = walletService.depositDividend(
                request.investorId(), request.idempotencyKey(), request.settlementBatchId(), request.amount());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, "배당금 입금이 완료되었습니다.")
        );
    }

    @Operation(
            summary = "자산종료 정산금 입금",
            description = "Settlement 서비스가 자산종료 정산 원금 반환 시 호출한다. 배당금 입금 API와 동일하게 KYC 체크는 하지 않으며, "
                    + "Idempotency 처리 방식도 동일하다."
    )
    @PostMapping("/settlements")
    public ResponseEntity<ApiResponse<SettlementDepositResponse>> depositSettlement(
            @Valid @RequestBody SettlementDepositRequest request
    ) {
        SettlementDepositResponse response = walletService.depositSettlement(
                request.investorId(), request.idempotencyKey(), request.finalSettlementBatchId(), request.amount());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, "정산금 입금이 완료되었습니다.")
        );
    }
}

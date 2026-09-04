package com.moneykk.moneytown.wallet.controller;

// Settlement 서비스가 배당/자산종료 정산 지급 시 호출하는 내부 전용 API.
// /api/v1/internal/{dividends,settlements}로 각각 독립된 리소스다.
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.wallet.dto.request.DividendDepositRequest;
import com.moneykk.moneytown.wallet.dto.request.SettlementDepositRequest;
import com.moneykk.moneytown.wallet.dto.response.DividendDepositResponse;
import com.moneykk.moneytown.wallet.dto.response.SettlementDepositResponse;
import com.moneykk.moneytown.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//TODO: 인가 코드 추가 (Settlement 서비스 전용 내부 호출임을 검증)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal")
public class WalletInternalDepositController {

    private final WalletService walletService;

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

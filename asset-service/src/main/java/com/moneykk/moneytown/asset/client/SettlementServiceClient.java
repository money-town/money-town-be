package com.moneykk.moneytown.asset.client;

import com.moneykk.moneytown.asset.dto.request.FinalSettlementOpenRequest;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** 정산 서비스 내부 API */
@FeignClient(name = "settlement-service")
public interface SettlementServiceClient {

    @PostMapping("/api/v1/internal/final-settlements")
    void openFinalSettlement(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @RequestBody FinalSettlementOpenRequest request
    );
}

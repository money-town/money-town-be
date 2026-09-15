package com.moneykk.moneytown.wallet.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionReservedPayload;
import com.moneykk.moneytown.wallet.dto.request.DividendDepositRequest;
import com.moneykk.moneytown.wallet.dto.request.SettlementDepositRequest;
import com.moneykk.moneytown.wallet.dto.request.TransactionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

// 다른 서비스가 필드를 추가해도(예: Offering/Settlement 쪽 스키마 진화) Wallet이 깨지지 않는지 확인.
class JsonToleranceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("SubscriptionReservedPayload는 모르는 필드가 와도 역직렬화가 실패하지 않는다")
    void subscriptionReservedPayload_toleratesUnknownField() {
        String json = "{\"amount\": 1000, \"assetId\": \"a1\"}";

        assertDoesNotThrow(() -> objectMapper.readValue(json, SubscriptionReservedPayload.class));
    }

    @Test
    @DisplayName("DividendDepositRequest는 모르는 필드가 와도 역직렬화가 실패하지 않는다")
    void dividendDepositRequest_toleratesUnknownField() {
        String json = """
                {"idempotencyKey": "key-1", "investorId": "11111111-1111-1111-1111-111111111111",
                 "settlementBatchId": "22222222-2222-2222-2222-222222222222", "amount": 1000, "batchName": "1차"}
                """;

        assertDoesNotThrow(() -> objectMapper.readValue(json, DividendDepositRequest.class));
    }

    @Test
    @DisplayName("SettlementDepositRequest는 모르는 필드가 와도 역직렬화가 실패하지 않는다")
    void settlementDepositRequest_toleratesUnknownField() {
        String json = """
                {"idempotencyKey": "key-1", "investorId": "11111111-1111-1111-1111-111111111111",
                 "finalSettlementBatchId": "22222222-2222-2222-2222-222222222222", "amount": 1000, "assetName": "자산A"}
                """;

        assertDoesNotThrow(() -> objectMapper.readValue(json, SettlementDepositRequest.class));
    }

    @Test
    @DisplayName("TransactionRequest는 모르는 필드가 와도 역직렬화가 실패하지 않는다")
    void transactionRequest_toleratesUnknownField() {
        String json = "{\"amount\": 1000, \"memo\": \"테스트\"}";

        assertDoesNotThrow(() -> objectMapper.readValue(json, TransactionRequest.class));
    }
}

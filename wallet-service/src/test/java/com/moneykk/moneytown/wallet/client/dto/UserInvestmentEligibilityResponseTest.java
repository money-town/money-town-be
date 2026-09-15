package com.moneykk.moneytown.wallet.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserInvestmentEligibilityResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("응답에 모르는 필드(예: userRole)가 추가돼도 역직렬화가 실패하지 않는다")
    void deserialize_withUnknownField_doesNotThrow() {
        String json = """
                {
                  "userId": "11111111-1111-1111-1111-111111111111",
                  "accountStatus": "ACTIVE",
                  "kycStatus": "VERIFIED",
                  "kycExpiresAt": "2099-01-01T00:00:00Z",
                  "userRole": "INVESTOR"
                }
                """;

        UserInvestmentEligibilityResponse response = assertDoesNotThrow(
                () -> objectMapper.readValue(json, UserInvestmentEligibilityResponse.class));

        assertTrue(response.isEligibleForTransaction());
    }
}

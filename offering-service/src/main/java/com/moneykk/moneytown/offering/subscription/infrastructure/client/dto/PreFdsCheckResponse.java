package com.moneykk.moneytown.offering.subscription.infrastructure.client.dto;

/**
 * Analysis Service의 Pre-FDS 검사 응답 DTO.
 *
 * result   : FDS 검사 결과 (PASS / BLOCK)
 * ruleCode : BLOCK인 경우 탐지된 FDS 규칙 코드
 */
public record PreFdsCheckResponse(
        String result,
        String ruleCode
) {

    public boolean isPass() {
        return "PASS".equalsIgnoreCase(result);
    }

    public boolean isBlock() {
        return "BLOCK".equalsIgnoreCase(result);
    }
}
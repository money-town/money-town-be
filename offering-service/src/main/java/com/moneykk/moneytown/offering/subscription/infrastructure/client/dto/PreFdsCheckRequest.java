package com.moneykk.moneytown.offering.subscription.infrastructure.client.dto;

import java.util.UUID;

/**
 * Analysis Service의 Pre-FDS 검사 요청 DTO.
 *
 * requestId : 개별 Pre-FDS 호출 식별자
 * userId    : 청약 요청 사용자 ID
 * assetId   : 청약 대상 공모의 자산 ID
 */
public record PreFdsCheckRequest(
        UUID requestId,
        UUID userId,
        UUID assetId
) {
}
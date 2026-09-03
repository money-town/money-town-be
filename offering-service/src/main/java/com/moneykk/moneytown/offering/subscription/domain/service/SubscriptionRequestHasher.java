package com.moneykk.moneytown.offering.subscription.domain.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 청약 요청의 비즈니스 데이터를 기준으로
 * 멱등성 검증에 사용할 SHA-256 요청 해시를 생성한다.
 *
 * 동일 Idempotency-Key가 재사용되었을 때
 * offeringId와 quantity가 기존 요청과 동일한지 검증하는 데 사용한다.
 */
@Component
public class SubscriptionRequestHasher {

    /**
     * 청약 요청을 식별할 SHA-256 해시를 생성한다.
     *
     * userId는 멱등성 UNIQUE 조건
     * (userId, operation, idempotencyKey)에 별도로 포함되므로
     * 요청 해시에는 비즈니스 요청 데이터인 offeringId와 quantity를 사용한다.
     */
    public String hash(
            UUID offeringId,
            Long quantity
    ) {
        if (offeringId == null) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
            );
        }

        if (quantity == null) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_QUANTITY
            );
        }

        String source = offeringId + ":" + quantity;

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hashed = digest.digest(
                    source.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashed);

        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(
                    SubscriptionErrorCode.REQUEST_HASH_GENERATION_FAILED
            );
        }
    }
}
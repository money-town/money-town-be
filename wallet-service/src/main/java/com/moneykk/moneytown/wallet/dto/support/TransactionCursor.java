package com.moneykk.moneytown.wallet.dto.support;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

// (createdAt, transactionId)를 opaque 토큰으로 감싼다.
public final class TransactionCursor {

    private TransactionCursor() {
    }

    public static String encode(Instant createdAt, Long id) {
        String raw = createdAt.toEpochMilli() + "_" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Decoded decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("_", 2);
            return new Decoded(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            throw new BusinessException(WalletErrorCode.INVALID_CURSOR);
        }
    }

    public record Decoded(Instant createdAt, Long id) {
    }
}

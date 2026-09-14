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

    // 밀리초(toEpochMilli)로 자르면 DB의 마이크로초 정밀도가 손실돼 거래가 스킵될 수 있어 초+나노로 담는다.
    public static String encode(Instant createdAt, Long id) {
        String raw = createdAt.getEpochSecond() + "." + createdAt.getNano() + "_" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Decoded decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("_", 2);
            String[] secondAndNano = parts[0].split("\\.", 2);
            Instant createdAt = Instant.ofEpochSecond(Long.parseLong(secondAndNano[0]), Long.parseLong(secondAndNano[1]));
            return new Decoded(createdAt, Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            throw new BusinessException(WalletErrorCode.INVALID_CURSOR);
        }
    }

    public record Decoded(Instant createdAt, Long id) {
    }
}

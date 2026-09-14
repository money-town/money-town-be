package com.moneykk.moneytown.wallet.dto.support;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionCursorTest {

    @Test
    @DisplayName("인코딩한 커서를 디코딩하면 원래 createdAt/id로 복원된다")
    void encodeThenDecode_roundTrips() {
        Instant createdAt = Instant.parse("2026-09-13T00:00:00Z");

        String cursor = TransactionCursor.encode(createdAt, 42L);
        TransactionCursor.Decoded decoded = TransactionCursor.decode(cursor);

        assertThat(decoded.createdAt()).isEqualTo(createdAt);
        assertThat(decoded.id()).isEqualTo(42L);
    }

    @Test
    @DisplayName("마이크로초 단위 정밀도도 잘리지 않고 그대로 보존된다")
    void encodeThenDecode_preservesMicrosecondPrecision() {
        Instant createdAt = Instant.parse("2026-09-13T09:54:59.066394Z");

        String cursor = TransactionCursor.encode(createdAt, 1L);
        TransactionCursor.Decoded decoded = TransactionCursor.decode(cursor);

        assertThat(decoded.createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("깨진 커서 문자열을 디코딩하면 INVALID_CURSOR 예외를 던진다")
    void decode_invalidCursor_throwsBusinessException() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> TransactionCursor.decode("not-a-valid-cursor!!"));

        assertThat(exception.getErrorCode()).isEqualTo(WalletErrorCode.INVALID_CURSOR);
    }
}

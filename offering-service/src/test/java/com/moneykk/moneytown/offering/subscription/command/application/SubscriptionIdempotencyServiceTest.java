package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionIdempotencyServiceTest {

    @Mock
    private IdempotencyRequestRepository idempotencyRequestRepository;

    @InjectMocks
    private SubscriptionIdempotencyService subscriptionIdempotencyService;

    @Test
    @DisplayName("멱등 요청을 선점하면 선점 결과를 그대로 반환한다")
    void tryBeginDelegatesToRepository() {
        // given
        UUID idempotencyRequestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(idempotencyRequestRepository.tryInsert(
                idempotencyRequestId,
                userId,
                "CREATE_SUBSCRIPTION",
                "idempotency-key",
                "request-hash",
                "SUBSCRIPTION"
        )).thenReturn(1);

        // when
        int result = subscriptionIdempotencyService.tryBegin(
                idempotencyRequestId,
                userId,
                "CREATE_SUBSCRIPTION",
                "idempotency-key",
                "request-hash",
                "SUBSCRIPTION"
        );

        // then
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("동일한 Idempotency-Key 요청이 이미 존재하면 0을 반환한다")
    void tryBeginReturnsZeroWhenAlreadyExists() {
        // given
        UUID idempotencyRequestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(idempotencyRequestRepository.tryInsert(
                idempotencyRequestId,
                userId,
                "CREATE_SUBSCRIPTION",
                "idempotency-key",
                "request-hash",
                "SUBSCRIPTION"
        )).thenReturn(0);

        // when
        int result = subscriptionIdempotencyService.tryBegin(
                idempotencyRequestId,
                userId,
                "CREATE_SUBSCRIPTION",
                "idempotency-key",
                "request-hash",
                "SUBSCRIPTION"
        );

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("청약 처리 실패를 기록한다")
    void failDelegatesToRepository() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        subscriptionIdempotencyService.fail(
                userId,
                "CREATE_SUBSCRIPTION",
                "idempotency-key",
                500
        );

        // then
        verify(idempotencyRequestRepository).fail(
                userId,
                "CREATE_SUBSCRIPTION",
                "idempotency-key",
                500
        );
    }
}

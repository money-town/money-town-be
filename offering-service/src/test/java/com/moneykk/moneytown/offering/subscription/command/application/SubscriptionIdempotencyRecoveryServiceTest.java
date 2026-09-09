package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionIdempotencyRecoveryServiceTest {

    private static final long PROCESSING_TIMEOUT_SECONDS = 300L;
    private static final int BATCH_SIZE = 100;

    @Mock
    private IdempotencyRequestRepository idempotencyRequestRepository;

    @InjectMocks
    private SubscriptionIdempotencyRecoveryService
            subscriptionIdempotencyRecoveryService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                subscriptionIdempotencyRecoveryService,
                "processingTimeoutSeconds",
                PROCESSING_TIMEOUT_SECONDS
        );
    }

    @Test
    @DisplayName("처리 제한 시간을 초과한 PROCESSING 멱등 요청을 FAILED로 복구한다")
    void recoversExpiredProcessingRequests() {
        // given
        Instant databaseNow =
                Instant.parse("2026-09-07T10:00:00Z");

        Instant expiredBefore =
                databaseNow.minusSeconds(PROCESSING_TIMEOUT_SECONDS);

        when(idempotencyRequestRepository.getCurrentDatabaseTime())
                .thenReturn(databaseNow);

        when(idempotencyRequestRepository
                .recoverExpiredProcessingRequests(
                        expiredBefore,
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        BATCH_SIZE
                ))
                .thenReturn(3);

        // when
        int recovered =
                subscriptionIdempotencyRecoveryService
                        .recoverExpiredProcessing(BATCH_SIZE);

        // then
        assertThat(recovered).isEqualTo(3);

        verify(idempotencyRequestRepository)
                .getCurrentDatabaseTime();

        verify(idempotencyRequestRepository)
                .recoverExpiredProcessingRequests(
                        expiredBefore,
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        BATCH_SIZE
                );
    }

    @Test
    @DisplayName("복구 배치 크기가 1보다 작으면 요청을 처리하지 않는다")
    void rejectsInvalidBatchSize() {
        // when & then
        assertThatThrownBy(() ->
                subscriptionIdempotencyRecoveryService
                        .recoverExpiredProcessing(0)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize는 1 이상이어야 합니다.");

        verifyNoInteractions(idempotencyRequestRepository);
    }

    @Test
    @DisplayName("멱등 요청 처리 제한 시간이 1초보다 작으면 복구하지 않는다")
    void rejectsInvalidProcessingTimeout() {
        // given
        ReflectionTestUtils.setField(
                subscriptionIdempotencyRecoveryService,
                "processingTimeoutSeconds",
                0L
        );

        // when & then
        assertThatThrownBy(() ->
                subscriptionIdempotencyRecoveryService
                        .recoverExpiredProcessing(BATCH_SIZE)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("멱등 요청 처리 제한 시간은 1초 이상이어야 합니다.");

        verifyNoInteractions(idempotencyRequestRepository);
    }
}
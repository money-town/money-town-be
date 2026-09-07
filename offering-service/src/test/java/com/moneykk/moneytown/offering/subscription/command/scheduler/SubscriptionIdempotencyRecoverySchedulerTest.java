package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionIdempotencyRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionIdempotencyRecoverySchedulerTest {

    private static final int RECOVERY_BATCH_SIZE = 100;

    @Mock
    private SubscriptionIdempotencyRecoveryService
            subscriptionIdempotencyRecoveryService;

    @InjectMocks
    private SubscriptionIdempotencyRecoveryScheduler
            subscriptionIdempotencyRecoveryScheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                subscriptionIdempotencyRecoveryScheduler,
                "recoveryBatchSize",
                RECOVERY_BATCH_SIZE
        );
    }

    @Test
    @DisplayName("스케줄러는 설정된 배치 크기로 오래된 멱등 요청 복구를 실행한다")
    void recoversExpiredProcessingRequests() {
        // given
        when(subscriptionIdempotencyRecoveryService
                .recoverExpiredProcessing(RECOVERY_BATCH_SIZE))
                .thenReturn(3);

        // when
        subscriptionIdempotencyRecoveryScheduler
                .recoverExpiredProcessingRequests();

        // then
        verify(subscriptionIdempotencyRecoveryService)
                .recoverExpiredProcessing(RECOVERY_BATCH_SIZE);
    }

    @Test
    @DisplayName("멱등 요청 복구 중 예외가 발생해도 다음 스케줄 실행을 위해 예외를 전파하지 않는다")
    void doesNotPropagateRecoveryFailure() {
        // given
        when(subscriptionIdempotencyRecoveryService
                .recoverExpiredProcessing(RECOVERY_BATCH_SIZE))
                .thenThrow(new RuntimeException("DB 오류"));

        // when & then
        assertThatCode(() ->
                subscriptionIdempotencyRecoveryScheduler
                        .recoverExpiredProcessingRequests()
        ).doesNotThrowAnyException();

        verify(subscriptionIdempotencyRecoveryService)
                .recoverExpiredProcessing(RECOVERY_BATCH_SIZE);
    }
}
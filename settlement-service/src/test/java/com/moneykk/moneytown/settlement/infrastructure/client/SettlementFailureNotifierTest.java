package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.NotificationRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.NotificationType;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementFailureNotifierTest {

    @Mock
    private AnalysisServiceClient analysisServiceClient;

    @InjectMocks
    private SettlementFailureNotifier settlementFailureNotifier;

    @Test
    @DisplayName("배당 정산 회차 실패를 배치ID를 멱등키로 삼아 SETTLEMENT_FAILED 알림으로 통보한다")
    void notifiesDividendBatchFailure() {
        SettlementBatch batch = SettlementBatch.open(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1), 1_000_000L, 0L);
        ReflectionTestUtils.setField(batch, "status", SettlementStatus.FAILED);

        settlementFailureNotifier.notifyDividendBatchFailed(batch);

        ArgumentCaptor<NotificationRequest> requestCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(analysisServiceClient).sendNotification(eq(batch.getId()), requestCaptor.capture());
        assertThat(requestCaptor.getValue().notificationType()).isEqualTo(NotificationType.SETTLEMENT_FAILED);
        assertThat(requestCaptor.getValue().userId()).isNull();
        assertThat(requestCaptor.getValue().title()).isNotBlank();
        assertThat(requestCaptor.getValue().message()).isNotBlank();
    }

    @Test
    @DisplayName("최종 정산 회차 실패를 배치ID를 멱등키로 삼아 SETTLEMENT_FAILED 알림으로 통보한다")
    void notifiesFinalSettlementBatchFailure() {
        FinalSettlementBatch batch = FinalSettlementBatch.open(UUID.randomUUID(), Instant.now(), 1_000_000L, 900_000_000L);
        ReflectionTestUtils.setField(batch, "status", SettlementStatus.PARTIAL_FAILED);

        settlementFailureNotifier.notifyFinalSettlementBatchFailed(batch);

        ArgumentCaptor<NotificationRequest> requestCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(analysisServiceClient).sendNotification(eq(batch.getId()), requestCaptor.capture());
        assertThat(requestCaptor.getValue().notificationType()).isEqualTo(NotificationType.SETTLEMENT_FAILED);
        assertThat(requestCaptor.getValue().userId()).isNull();
    }

    @Test
    @DisplayName("analysis-service 호출이 실패해도 예외를 전파하지 않는다")
    void swallowsFailureWithoutPropagating() {
        SettlementBatch batch = SettlementBatch.open(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1), 1_000_000L, 0L);
        ReflectionTestUtils.setField(batch, "status", SettlementStatus.FAILED);
        when(analysisServiceClient.sendNotification(any(), any())).thenThrow(mock(FeignException.class));

        assertThatCode(() -> settlementFailureNotifier.notifyDividendBatchFailed(batch))
                .doesNotThrowAnyException();
    }
}
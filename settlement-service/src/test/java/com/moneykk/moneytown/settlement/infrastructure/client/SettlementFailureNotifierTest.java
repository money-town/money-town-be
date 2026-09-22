package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.NotificationResponse;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.NotificationRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.NotificationType;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
        SettlementBatch batch = SettlementBatch.open(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1), 1_000_000L);
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

    @Nested
    @DisplayName("미해결 실패 회차 재통보 (T5/T6)")
    class RemindUnresolved {

        // KST 2026-09-20 12:00
        private final Instant now = Instant.parse("2026-09-20T03:00:00Z");
        private final UUID waitingRevenueId = UUID.randomUUID();

        @Test
        @DisplayName("실패로 확정된 지 1시간이 안 됐으면 최초 알림에 맡기고 아무것도 호출하지 않는다")
        void skipsWithinInitialGrace() {
            SettlementBatch batch = failedBatch(now.minus(Duration.ofMinutes(30)));

            settlementFailureNotifier.remindUnresolvedDividendBatch(batch, null, now);

            verifyNoInteractions(analysisServiceClient);
        }

        @ParameterizedTest
        @ValueSource(strings = {"SENT", "PENDING"})
        @DisplayName("최초 알림이 전달(또는 발송 중)이고 하루가 안 지났으면 최초 키로 상태만 확인하고 추가 알림은 보내지 않는다")
        void onlyChecksInitialAlertWhenDeliveredAndUnderOneDay(String status) {
            SettlementBatch batch = failedBatch(now.minus(Duration.ofHours(3)));
            when(analysisServiceClient.sendNotification(eq(batch.getId()), any())).thenReturn(response(status));

            settlementFailureNotifier.remindUnresolvedDividendBatch(batch, null, now);

            verify(analysisServiceClient, times(1)).sendNotification(any(), any());
        }

        @Test
        @DisplayName("최초 알림이 Slack에서 FAILED면 날짜 키로 즉시 재통보한다 (다음 날까지 기다리지 않음)")
        void resendsWhenInitialAlertFailedInSlack() {
            SettlementBatch batch = failedBatch(now.minus(Duration.ofHours(3)));
            when(analysisServiceClient.sendNotification(eq(batch.getId()), any())).thenReturn(response("FAILED"));

            settlementFailureNotifier.remindUnresolvedDividendBatch(batch, null, now);

            ArgumentCaptor<UUID> keys = ArgumentCaptor.forClass(UUID.class);
            ArgumentCaptor<NotificationRequest> requests = ArgumentCaptor.forClass(NotificationRequest.class);
            verify(analysisServiceClient, times(2)).sendNotification(keys.capture(), requests.capture());
            assertThat(keys.getAllValues().get(0)).isEqualTo(batch.getId());
            assertThat(keys.getAllValues().get(1)).isNotEqualTo(batch.getId());
            assertThat(requests.getAllValues().get(1).notificationType()).isEqualTo(NotificationType.SETTLEMENT_FAILED);
            assertThat(requests.getAllValues().get(1).message()).contains("1일째", batch.getId().toString());
        }

        @Test
        @DisplayName("최초 알림 상태 확인 호출 자체가 실패하면(analysis 미도달) 날짜 키 재통보를 시도하고 예외는 전파하지 않는다")
        void retriesWhenInitialCheckFails() {
            SettlementBatch batch = failedBatch(now.minus(Duration.ofHours(3)));
            when(analysisServiceClient.sendNotification(eq(batch.getId()), any())).thenThrow(mock(FeignException.class));
            when(analysisServiceClient.sendNotification(argThat(k -> !batch.getId().equals(k)), any())).thenThrow(mock(FeignException.class));

            assertThatCode(() -> settlementFailureNotifier.remindUnresolvedDividendBatch(batch, null, now)).doesNotThrowAnyException();

            verify(analysisServiceClient, times(2)).sendNotification(any(), any());
        }

        @Test
        @DisplayName("최초 알림이 전달됐어도 하루 넘게 미해결이면 날짜 키로 재통보한다 — retryBatch 후 재실패가 조용해지는 것을 막는다")
        void remindsDailyWhenUnresolvedOverOneDay() {
            SettlementBatch batch = failedBatch(now.minus(Duration.ofDays(2)).minus(Duration.ofHours(3)));
            when(analysisServiceClient.sendNotification(eq(batch.getId()), any())).thenReturn(response("SENT"));

            settlementFailureNotifier.remindUnresolvedDividendBatch(batch, null, now);

            ArgumentCaptor<NotificationRequest> requests = ArgumentCaptor.forClass(NotificationRequest.class);
            verify(analysisServiceClient, times(2)).sendNotification(any(), requests.capture());
            assertThat(requests.getAllValues().get(1).title()).isEqualTo("배당 정산 회차 실패 미해결");
            assertThat(requests.getAllValues().get(1).message()).contains("3일째", "PARTIAL_FAILED");
            assertThat(requests.getAllValues().get(1).message()).doesNotContain("차단됨");
        }

        @Test
        @DisplayName("대기 중인 수익이 있으면(T5 차단) 메시지에 revenueId와 차단 사실을 담는다")
        void includesWaitingRevenueWhenBlocking() {
            SettlementBatch batch = failedBatch(now.minus(Duration.ofDays(1)).minus(Duration.ofHours(1)));
            when(analysisServiceClient.sendNotification(eq(batch.getId()), any())).thenReturn(response("SENT"));

            settlementFailureNotifier.remindUnresolvedDividendBatch(batch, waitingRevenueId, now);

            ArgumentCaptor<NotificationRequest> requests = ArgumentCaptor.forClass(NotificationRequest.class);
            verify(analysisServiceClient, times(2)).sendNotification(any(), requests.capture());
            assertThat(requests.getAllValues().get(1).message()).contains(waitingRevenueId.toString(), "차단됨");
        }

        @Test
        @DisplayName("날짜 키는 KST 기준으로 하루 안에서는 같고 KST 자정을 넘으면 바뀐다 (UTC 자정이 아님)")
        void dailyKeyFollowsKstDay() {
            SettlementBatch batch = failedBatch(Instant.parse("2026-09-10T00:00:00Z"));
            when(analysisServiceClient.sendNotification(eq(batch.getId()), any())).thenReturn(response("SENT"));

            Instant kstMorning = Instant.parse("2026-09-20T00:00:00Z");     // KST 09-20 09:00 (UTC 자정 직후)
            Instant kstLateNight = Instant.parse("2026-09-20T14:59:59Z");   // KST 09-20 23:59:59
            Instant kstNextDay = Instant.parse("2026-09-20T15:00:00Z");     // KST 09-21 00:00:00
            settlementFailureNotifier.remindUnresolvedDividendBatch(batch, null, kstMorning);
            settlementFailureNotifier.remindUnresolvedDividendBatch(batch, null, kstLateNight);
            settlementFailureNotifier.remindUnresolvedDividendBatch(batch, null, kstNextDay);

            ArgumentCaptor<UUID> keys = ArgumentCaptor.forClass(UUID.class);
            verify(analysisServiceClient, times(6)).sendNotification(keys.capture(), any());
            List<UUID> dailyKeys = List.of(keys.getAllValues().get(1), keys.getAllValues().get(3), keys.getAllValues().get(5));
            assertThat(dailyKeys.get(0)).isEqualTo(dailyKeys.get(1));
            assertThat(dailyKeys.get(1)).isNotEqualTo(dailyKeys.get(2));
        }

        @Test
        @DisplayName("7일 이상 방치되면 제목이 장기 미해결로 바뀌지만 알림은 멈추지 않는다")
        void escalatesTitleAfterSevenDays() {
            SettlementBatch batch = failedBatch(now.minus(Duration.ofDays(8)));
            when(analysisServiceClient.sendNotification(eq(batch.getId()), any())).thenReturn(response("SENT"));

            settlementFailureNotifier.remindUnresolvedDividendBatch(batch, null, now);

            ArgumentCaptor<NotificationRequest> requests = ArgumentCaptor.forClass(NotificationRequest.class);
            verify(analysisServiceClient, times(2)).sendNotification(any(), requests.capture());
            assertThat(requests.getAllValues().get(1).title()).startsWith("[장기 미해결]").contains("9일째");
        }

        @Test
        @DisplayName("최종 정산 회차도 같은 규칙으로 재통보하고 라벨이 최종 정산이다")
        void remindsFinalSettlementBatch() {
            FinalSettlementBatch batch = FinalSettlementBatch.open(UUID.randomUUID(), Instant.now(), 1_000_000L, 900_000_000L);
            ReflectionTestUtils.setField(batch, "status", SettlementStatus.FAILED);
            ReflectionTestUtils.setField(batch, "updatedAt", now.minus(Duration.ofDays(1)).minus(Duration.ofHours(2)));
            when(analysisServiceClient.sendNotification(eq(batch.getId()), any())).thenReturn(response("SENT"));

            settlementFailureNotifier.remindUnresolvedFinalSettlementBatch(batch, now);

            ArgumentCaptor<NotificationRequest> requests = ArgumentCaptor.forClass(NotificationRequest.class);
            verify(analysisServiceClient, times(2)).sendNotification(any(), requests.capture());
            assertThat(requests.getAllValues().get(1).title()).isEqualTo("최종 정산 회차 실패 미해결");
        }

        private SettlementBatch failedBatch(Instant stuckSince) {
            SettlementBatch batch = SettlementBatch.open(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1), 1_000_000L);
            ReflectionTestUtils.setField(batch, "status", SettlementStatus.PARTIAL_FAILED);
            ReflectionTestUtils.setField(batch, "updatedAt", stuckSince);
            return batch;
        }

        private ApiResponse<NotificationResponse> response(String status) {
            return ApiResponse.success(new NotificationResponse(UUID.randomUUID(), "SETTLEMENT_FAILED", status, null), null);
        }
    }

    @Test
    @DisplayName("analysis-service 호출이 실패해도 예외를 전파하지 않는다")
    void swallowsFailureWithoutPropagating() {
        SettlementBatch batch = SettlementBatch.open(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1), 1_000_000L);
        ReflectionTestUtils.setField(batch, "status", SettlementStatus.FAILED);
        when(analysisServiceClient.sendNotification(any(), any())).thenThrow(mock(FeignException.class));

        assertThatCode(() -> settlementFailureNotifier.notifyDividendBatchFailed(batch))
                .doesNotThrowAnyException();
    }
}
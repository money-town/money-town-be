package com.moneykk.moneytown.offering.global.outbox;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublishMonitorTest {

    @Mock
    private OutboxPublishService outboxPublishService;

    private SimpleMeterRegistry meterRegistry;
    private OutboxPublishMonitor monitor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        monitor = new OutboxPublishMonitor(
                outboxPublishService,
                meterRegistry,
                2
        );
    }

    @Test
    @DisplayName("설정한 최대 개수까지만 발행 슬롯을 확보한다")
    void reservesSlotsUpToConfiguredLimit() {
        int firstReserved =
                monitor.reserveSlots(2);

        int secondReserved =
                monitor.reserveSlots(1);

        assertThat(firstReserved).isEqualTo(2);
        assertThat(secondReserved).isZero();

        assertThat(inFlightGaugeValue())
                .isEqualTo(2.0);

        monitor.releaseUnusedSlots(2);

        assertThat(inFlightGaugeValue())
                .isZero();
    }

    @Test
    @DisplayName("요청한 수가 최대 동시 처리 수보다 크면 가능한 슬롯만 확보한다")
    void reservesOnlyAvailableSlots() {
        int reserved =
                monitor.reserveSlots(10);

        assertThat(reserved).isEqualTo(2);

        assertThat(inFlightGaugeValue())
                .isEqualTo(2.0);

        monitor.releaseUnusedSlots(reserved);

        assertThat(inFlightGaugeValue())
                .isZero();
    }

    @Test
    @DisplayName("발행 성공 시 성공 지연 시간을 기록하고 슬롯을 반환한다")
    void recordsSuccessLatencyAndReleasesSlot() {
        int reserved =
                monitor.reserveSlots(1);

        assertThat(reserved).isEqualTo(1);

        OutboxPublishMonitor.PublishAttempt attempt =
                monitor.startAttempt();

        monitor.completeSuccess(attempt);

        assertThat(inFlightGaugeValue())
                .isZero();

        Timer successTimer = meterRegistry
                .get("outbox.publish.latency")
                .tag("result", "success")
                .timer();

        Timer failureTimer = meterRegistry
                .get("outbox.publish.latency")
                .tag("result", "failure")
                .timer();

        assertThat(successTimer.count())
                .isEqualTo(1);

        assertThat(failureTimer.count())
                .isZero();

        // 슬롯이 반환되어 다시 최대 개수만큼 확보할 수 있다.
        int reservedAgain =
                monitor.reserveSlots(2);

        assertThat(reservedAgain).isEqualTo(2);

        monitor.releaseUnusedSlots(reservedAgain);
    }

    @Test
    @DisplayName("발행 실패 시 실패 지연 시간을 기록하고 슬롯을 반환한다")
    void recordsFailureLatencyAndReleasesSlot() {
        int reserved =
                monitor.reserveSlots(1);

        assertThat(reserved).isEqualTo(1);

        OutboxPublishMonitor.PublishAttempt attempt =
                monitor.startAttempt();

        monitor.completeFailure(attempt);

        assertThat(inFlightGaugeValue())
                .isZero();

        Timer successTimer = meterRegistry
                .get("outbox.publish.latency")
                .tag("result", "success")
                .timer();

        Timer failureTimer = meterRegistry
                .get("outbox.publish.latency")
                .tag("result", "failure")
                .timer();

        assertThat(successTimer.count())
                .isZero();

        assertThat(failureTimer.count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("같은 발행 시도를 중복 완료해도 슬롯은 한 번만 반환한다")
    void releasesSlotOnlyOnceForSameAttempt() {
        int reserved =
                monitor.reserveSlots(1);

        assertThat(reserved).isEqualTo(1);

        OutboxPublishMonitor.PublishAttempt attempt =
                monitor.startAttempt();

        monitor.completeSuccess(attempt);

        // 같은 콜백의 예외 처리 경로가 다시 호출된 상황을 가정한다.
        monitor.completeFailure(attempt);

        assertThat(inFlightGaugeValue())
                .isZero();

        Timer successTimer = meterRegistry
                .get("outbox.publish.latency")
                .tag("result", "success")
                .timer();

        Timer failureTimer = meterRegistry
                .get("outbox.publish.latency")
                .tag("result", "failure")
                .timer();

        assertThat(successTimer.count())
                .isEqualTo(1);

        assertThat(failureTimer.count())
                .isZero();

        // 중복 반환이 없었으므로 최대 두 개만 다시 확보할 수 있다.
        int reservedAgain =
                monitor.reserveSlots(3);

        assertThat(reservedAgain).isEqualTo(2);

        monitor.releaseUnusedSlots(reservedAgain);
    }

    @Test
    @DisplayName("사용하지 않은 발행 슬롯을 반환한다")
    void releasesUnusedSlots() {
        int reserved =
                monitor.reserveSlots(2);

        assertThat(reserved).isEqualTo(2);

        monitor.releaseUnusedSlots(1);

        assertThat(inFlightGaugeValue())
                .isEqualTo(1.0);

        int reservedAgain =
                monitor.reserveSlots(2);

        assertThat(reservedAgain).isEqualTo(1);

        assertThat(inFlightGaugeValue())
                .isEqualTo(2.0);

        monitor.releaseUnusedSlots(2);

        assertThat(inFlightGaugeValue())
                .isZero();
    }

    @Test
    @DisplayName("DB의 PROCESSING 이벤트 건수를 Gauge에 반영한다")
    void refreshesProcessingEventCount() {
        when(outboxPublishService.countProcessingEvents())
                .thenReturn(7L);

        monitor.refreshProcessingEventCount();

        assertThat(processingGaugeValue())
                .isEqualTo(7.0);

        verify(outboxPublishService)
                .countProcessingEvents();
    }

    @Test
    @DisplayName("PROCESSING 건수 조회가 실패하면 기존 Gauge 값을 유지한다")
    void keepsPreviousProcessingCountWhenRefreshFails() {
        when(outboxPublishService.countProcessingEvents())
                .thenReturn(4L)
                .thenThrow(new IllegalStateException("DB unavailable"));

        monitor.refreshProcessingEventCount();

        assertThat(processingGaugeValue())
                .isEqualTo(4.0);

        assertThatCode(
                () -> monitor.refreshProcessingEventCount()
        ).doesNotThrowAnyException();

        assertThat(processingGaugeValue())
                . isEqualTo(4.0);

        verify(outboxPublishService, times(2))
                .countProcessingEvents();
    }

    @Test
    @DisplayName("최대 동시 발행 수는 1 이상이어야 한다")
    void rejectsInvalidMaxInFlight() {
        assertThatThrownBy(
                () -> new OutboxPublishMonitor(
                        outboxPublishService,
                        new SimpleMeterRegistry(),
                        0
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxInFlight는 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("요청할 발행 슬롯 수는 1 이상이어야 한다")
    void rejectsInvalidRequestedSlotCount() {
        assertThatThrownBy(
                () -> monitor.reserveSlots(0)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestedCount는 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("반환할 슬롯 수는 음수일 수 없다")
    void rejectsNegativeReleaseCount() {
        assertThatThrownBy(
                () -> monitor.releaseUnusedSlots(-1)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("반환할 슬롯 수는 음수일 수 없습니다.");
    }

    private double inFlightGaugeValue() {
        return meterRegistry
                .get("outbox.publish.inflight")
                .gauge()
                .value();
    }

    private double processingGaugeValue() {
        return meterRegistry
                .get("outbox.events.processing")
                .gauge()
                .value();
    }
}
package com.moneykk.moneytown.offering.offering.command.scheduler;

import com.moneykk.moneytown.offering.offering.command.application.OfferingStatusTransitionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingStatusSchedulerTest {

    @Mock
    private OfferingStatusTransitionService offeringStatusTransitionService;

    @Mock
    private OfferingSchedulerMetrics offeringSchedulerMetrics;

    @InjectMocks
    private OfferingStatusScheduler offeringStatusScheduler;

    @Test
    @DisplayName("스케줄러 실행 시 시작 시간이 도래한 공모의 OPEN 전환을 요청한다")
    void opensScheduledOfferings() {
        offeringStatusScheduler.openScheduledOfferings();

        verify(offeringStatusTransitionService)
                .openScheduledOfferings();
    }

    @Test
    @DisplayName("OPEN 전환 중 오류가 발생해도 스케줄러 밖으로 예외를 전파하지 않는다")
    void doesNotPropagateOpenScheduledOfferingsFailure() {
        when(offeringStatusTransitionService.openScheduledOfferings())
                .thenThrow(new RuntimeException("OPEN 전환 실패"));

        assertDoesNotThrow(
                () -> offeringStatusScheduler.openScheduledOfferings()
        );

        verify(offeringStatusTransitionService)
                .openScheduledOfferings();

        verify(offeringSchedulerMetrics)
                .recordOpenScheduledFailure();
    }

    @Test
    @DisplayName("스케줄러 실행 시 종료 시간이 도래한 SOLD_OUT 공모의 CLOSED 전환을 요청한다")
    void closesSoldOutOfferings() {
        offeringStatusScheduler.closeSoldOutOfferings();

        verify(offeringStatusTransitionService)
                .closeSoldOutOfferings();
    }

    @Test
    @DisplayName("CLOSED 전환 중 오류가 발생해도 스케줄러 밖으로 예외를 전파하지 않는다")
    void doesNotPropagateCloseSoldOutOfferingsFailure() {
        when(offeringStatusTransitionService.closeSoldOutOfferings())
                .thenThrow(new RuntimeException("CLOSED 전환 실패"));

        assertDoesNotThrow(
                () -> offeringStatusScheduler.closeSoldOutOfferings()
        );

        verify(offeringStatusTransitionService)
                .closeSoldOutOfferings();

        verify(offeringSchedulerMetrics)
                .recordCloseSoldOutFailure();
    }

    @Test
    @DisplayName("스케줄러 실행 시 모집 종료된 OPEN 공모의 모집 미달 취소 전환을 요청한다")
    void startsUnderSubscribedCancellations() {
        offeringStatusScheduler.startUnderSubscribedCancellations();

        verify(offeringStatusTransitionService)
                .startUnderSubscribedCancellations();

    }

    @Test
    @DisplayName("모집 미달 취소 처리 중 오류가 발생해도 스케줄러 밖으로 예외를 전파하지 않는다")
    void doesNotPropagateUnderSubscribedCancellationFailure() {
        when(
                offeringStatusTransitionService
                        .startUnderSubscribedCancellations()
        ).thenThrow(new RuntimeException("모집 미달 취소 처리 실패"));

        assertDoesNotThrow(
                () -> offeringStatusScheduler
                        .startUnderSubscribedCancellations()
        );

        verify(offeringStatusTransitionService)
                .startUnderSubscribedCancellations();

        verify(offeringSchedulerMetrics)
                .recordUnderSubscribedCancellationFailure();
    }
}
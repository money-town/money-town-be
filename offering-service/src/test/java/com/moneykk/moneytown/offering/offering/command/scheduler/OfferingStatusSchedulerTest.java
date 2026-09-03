package com.moneykk.moneytown.offering.offering.command.scheduler;

import com.moneykk.moneytown.offering.offering.command.application.OfferingStatusTransitionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OfferingStatusSchedulerTest {

    @Mock
    private OfferingStatusTransitionService offeringStatusTransitionService;

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
    @DisplayName("스케줄러 실행 시 종료 시간이 도래한 SOLD_OUT 공모의 CLOSED 전환을 요청한다")
    void closesSoldOutOfferings() {
        offeringStatusScheduler.closeSoldOutOfferings();

        verify(offeringStatusTransitionService)
                .closeSoldOutOfferings();
    }
}
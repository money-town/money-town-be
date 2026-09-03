package com.moneykk.moneytown.offering.offering.command.scheduler;

import com.moneykk.moneytown.offering.offering.command.application.OfferingStatusTransitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OfferingStatusScheduler {

    private final OfferingStatusTransitionService offeringStatusTransitionService;

    /**
     * 시작 시간이 도래한 SCHEDULED 공모를 OPEN 상태로 전환한다.
     *
     * 1분마다 실행되며 실제 상태 전이는 조건부 Bulk Update로 처리한다.
     */
    @Scheduled(cron = "0 * * * * *")
    public void openScheduledOfferings() {
        offeringStatusTransitionService.openScheduledOfferings();
    }

    /**
     * 모집 종료 시간이 도래한 SOLD_OUT 공모를 CLOSED 상태로 전환한다.
     */
    @Scheduled(cron = "0 * * * * *")
    public void closeSoldOutOfferings() {
        offeringStatusTransitionService.closeSoldOutOfferings();
    }
}
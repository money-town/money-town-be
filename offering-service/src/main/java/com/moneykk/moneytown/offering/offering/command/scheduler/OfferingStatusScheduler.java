package com.moneykk.moneytown.offering.offering.command.scheduler;

import com.moneykk.moneytown.offering.offering.command.application.OfferingStatusTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OfferingStatusScheduler {

    private final OfferingStatusTransitionService offeringStatusTransitionService;

    private final OfferingSchedulerMetrics offeringSchedulerMetrics;

    /**
     * 시작 시간이 도래한 SCHEDULED 공모를 OPEN 상태로 전환한다.
     * <p>
     * 1분마다 실행되며 실제 상태 전이는 조건부 Bulk Update로 처리한다.
     */
    @Scheduled(cron = "0 * * * * *")
    public void openScheduledOfferings() {
        try {
            offeringStatusTransitionService.openScheduledOfferings();
        } catch (Exception e) {

            offeringSchedulerMetrics.recordOpenScheduledFailure();
            log.error(
                    "SCHEDULED 공모 OPEN 전환 스케줄러 실패",
                    e
            );
        }
    }

    /**
     * 모집 종료 시간이 도래한 SOLD_OUT 공모를 CLOSED 상태로 전환한다.
     */
    @Scheduled(cron = "0 * * * * *")
    public void closeSoldOutOfferings() {
        try {
            offeringStatusTransitionService.closeSoldOutOfferings();
        } catch (Exception e) {

            offeringSchedulerMetrics.recordCloseSoldOutFailure();
            log.error(
                    "SOLD_OUT 공모 CLOSED 전환 스케줄러 실패",
                    e
            );
        }
    }

    /**
     * 모집 종료 시간이 도래했지만 잔여 수량이 남아 있는 OPEN 공모를
     * 모집 미달에 따른 CANCELLING 상태로 전환하고 청약 보상을 시작한다.
     * <p>
     * 한 번에 최대 100건씩 처리한다.
     */
    @Scheduled(cron = "0 * * * * *")
    public void startUnderSubscribedCancellations() {
        try {
            offeringStatusTransitionService.startUnderSubscribedCancellations();
        } catch (Exception e) {

            offeringSchedulerMetrics.recordUnderSubscribedCancellationFailure();
            log.error(
                    "모집 미달 공모 취소 처리 스케줄러 실패",
                    e
            );
        }
    }
}
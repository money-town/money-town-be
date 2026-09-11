package com.moneykk.moneytown.analysis.notification.command.application;

import com.moneykk.moneytown.analysis.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "notification_reaper")
public class NotificationStaleReaper {

    private final NotificationRepository notificationRepository;

    @Value("${notification.stale-timeout}")
    private Duration staleTimeout;

    @Scheduled(fixedDelayString = "${notification.reaper-interval-ms}")
    @Transactional
    public void failStaleProcessing(){
        Instant now = Instant.now();
        int failed = notificationRepository.failStaleProcessing(
                "알림 요청이 정체된 상태라 fail 처리 하겠습니다.",
                now, now.minus(staleTimeout)
        );

        if(failed > 0){
            log.warn("정제된 알림 요청 {} 건을 failed 처리", failed);
        }
    }
}

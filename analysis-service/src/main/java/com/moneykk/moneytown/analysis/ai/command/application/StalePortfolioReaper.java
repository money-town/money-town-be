package com.moneykk.moneytown.analysis.ai.command.application;

import com.moneykk.moneytown.analysis.ai.domain.repository.PortfolioRepository;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class StalePortfolioReaper {

    private final PortfolioRepository portfolioRepository;

    @Value("${spring.ai.portfolio.stale-timeout:PT10M}")
    private Duration staleTimeout;

    @Scheduled(fixedDelayString = "${spring.ai.portfolio.reaper-interval-ms:60000}")
    @Transactional
    public void failStaleProcessing(){
        Instant now = Instant.now();
        int failed = portfolioRepository.failStaleProcessing(
                "AI 생성이 시간 내 완료되지 않아 실패 처리되었습니다.",
                now, now.minus(staleTimeout)
        );
        if(failed > 0){
            log.warn("정체된 PROCESSING 포트폴리오 {} 건을 FAILED로 처리", failed);
        }
    }
}

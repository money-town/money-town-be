package com.moneykk.moneytown.analysis.ai.command.application;

import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioStore {

    private final PortfolioRepository portfolioRepository;

    public Optional<Portfolio> findByUserIdAndIdempotencyKey(UUID userId, UUID key){
        return portfolioRepository.findByUserIdAndIdempotencyKey(userId, key);
    }


    @Transactional
    public Portfolio claim(Portfolio portfolio){
        return portfolioRepository.saveAndFlush(portfolio);
    }

    @Transactional
    public boolean complete(UUID portfolioId, String responseJson, long processingMs){
        int updated = portfolioRepository.completeIfProcessing(
                portfolioId, responseJson, processingMs, Instant.now()
        );
        if(updated == 0){
            log.warn("포트폴리오 {} 가 Processing이 아님 - complete 무시", portfolioId);
        }
        return updated == 1;
    }

    @Transactional
    public boolean fail(UUID portfolioId, String errorMessage, long processingMs){
        int updated = portfolioRepository.failIfProcessing(
                portfolioId, errorMessage, processingMs, Instant.now()
        );
        if(updated == 0){
            log.warn("포트폴리오 {} 가 PROCESSING이 아님 - fail 무시", portfolioId);
        }
        return updated == 1;
    }


}

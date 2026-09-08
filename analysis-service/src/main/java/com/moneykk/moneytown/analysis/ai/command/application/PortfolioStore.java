package com.moneykk.moneytown.analysis.ai.command.application;

import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.repository.PortfolioRepository;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
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
    public void complete(UUID portfolioId, String responseJson, long processingMs){
        Portfolio p = portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)
                .orElseThrow(() -> new BusinessException(AnalysisErrorCode.AI_PORTFOLIO_NOT_FOUND));
        p.complete(responseJson, processingMs);
    }

    @Transactional
    public void fail(UUID portfolioId, String errorMessage, long processingMs){
        Portfolio p = portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)
                .orElseThrow(() -> new BusinessException(AnalysisErrorCode.AI_PORTFOLIO_NOT_FOUND));
        p.fail(errorMessage, processingMs);
    }


}

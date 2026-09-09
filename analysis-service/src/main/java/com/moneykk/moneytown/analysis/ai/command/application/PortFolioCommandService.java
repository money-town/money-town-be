package com.moneykk.moneytown.analysis.ai.command.application;

import com.moneykk.moneytown.analysis.ai.command.dto.CreatePortfolioRequest;
import com.moneykk.moneytown.analysis.ai.command.dto.CreatePortfolioResponse;
import com.moneykk.moneytown.analysis.ai.command.dto.DeletePortfolioResponse;
import com.moneykk.moneytown.analysis.ai.domain.AiStatus;
import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.repository.PortfolioRepository;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Service
@RequiredArgsConstructor
public class PortFolioCommandService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioStore portfolioStore;
    private final PortfolioGenerator portfolioGenerator;

    @Value("${spring.ai.openai.chat.options.model}")
    private String model;

    private static final String PROMPT_VERSION = "v1";

    public CreatePortfolioResponse createPortfolio(UUID userId, UUID key, CreatePortfolioRequest request) {
        
        Optional<Portfolio> existing = portfolioStore.findByUserIdAndIdempotencyKey(userId, key);
        if(existing.isPresent()){
            return CreatePortfolioResponse.from(existing.get());
        }

        Portfolio portfolio;
        try{
            portfolio = portfolioStore.claim(
                    Portfolio.builder()
                            .idempotencyKey(key)
                            .userId(userId)
                            .investmentAmount(request.investmentAmount())
                            .riskType(request.riskType())
                            .assetType(request.assetType())   // nullable 허용
                            .model(model)
                            .promptVersion(PROMPT_VERSION)
                            .build()
            );
        }catch (DataIntegrityViolationException e){
            return CreatePortfolioResponse.from(
                    portfolioStore.findByUserIdAndIdempotencyKey(userId,key).orElseThrow(
                            () -> new BusinessException(AnalysisErrorCode.AI_PORTFOLIO_NOT_FOUND)
                    )
            );
        }
        try{
            portfolioGenerator.generate(portfolio.getId());
        }catch (RejectedExecutionException e) {
            portfolioStore.fail(portfolio.getId(), "AI 처리량 초과로 요청이 거절되었습니다.", 0L);
            throw new BusinessException(AnalysisErrorCode.AI_CAPACITY_EXCEEDED);
        }


        return CreatePortfolioResponse.from(portfolio);
    }

    @Transactional
    public DeletePortfolioResponse deletePortfolio(String role, UUID userId, UUID portfolioId) {


        Portfolio portfolio = portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)
                .orElseThrow(() -> new BusinessException(AnalysisErrorCode.AI_PORTFOLIO_NOT_FOUND));

        if(!"ADMIN".equals(role) && !portfolio.getUserId().equals(userId)){
            throw new BusinessException(AnalysisErrorCode.AI_FORBIDDEN);
        }

        if(portfolio.getStatus().equals(AiStatus.PROCESSING)){
            throw new BusinessException(AnalysisErrorCode.AI_PROCESSING);
        }


        portfolio.softDelete(userId);

        return DeletePortfolioResponse.from(portfolio);
    }
}

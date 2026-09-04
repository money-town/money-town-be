package com.moneykk.moneytown.analysis.ai.query.application;

import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.repository.PortfolioRepository;
import com.moneykk.moneytown.analysis.ai.query.dto.PortfolioDetailResponse;
import com.moneykk.moneytown.analysis.ai.query.dto.PortfolioItemResponse;
import com.moneykk.moneytown.analysis.ai.query.dto.PortfolioSearchCondition;
import com.moneykk.moneytown.analysis.ai.query.repository.PortfolioQueryRepository;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PortfolioQueryService {

    private static final String ADMIN = "ADMIN";

    private final PortfolioRepository portfolioRepository;
    private final PortfolioQueryRepository portfolioQueryRepository;

    public PageResponse<PortfolioItemResponse> getPortfolios(PortfolioSearchCondition searchCondition, Pageable pageable){
        return PageResponse.from(portfolioQueryRepository.search(searchCondition, pageable), PortfolioItemResponse::from);
    }

    public PageResponse<PortfolioItemResponse> getMyPortfolios(UUID userId, PortfolioSearchCondition searchCondition, Pageable pageable){
        return PageResponse.from(portfolioQueryRepository.searchMyPortfolio(userId, searchCondition, pageable), PortfolioItemResponse::from);
    }

    public PortfolioDetailResponse getPortfolio(String role, UUID userId, UUID portfolioId){

        Portfolio portfolio = portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)
                .orElseThrow(() -> new BusinessException(AnalysisErrorCode.AI_PORTFOLIO_NOT_FOUND));

        if(!ADMIN.equals(role) && !portfolio.getUserId().equals(userId)){
            throw new BusinessException(AnalysisErrorCode.AI_FORBIDDEN);
        }

        return PortfolioDetailResponse.from(portfolio);
    }
}

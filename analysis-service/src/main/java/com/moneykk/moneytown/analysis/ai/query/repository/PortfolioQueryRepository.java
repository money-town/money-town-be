package com.moneykk.moneytown.analysis.ai.query.repository;

import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.query.dto.PortfolioSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PortfolioQueryRepository {
    Page<Portfolio> searchMyPortfolio(UUID userId,PortfolioSearchCondition searchCondition, Pageable pageable);
    Page<Portfolio> search(PortfolioSearchCondition searchCondition, Pageable pageable);
}

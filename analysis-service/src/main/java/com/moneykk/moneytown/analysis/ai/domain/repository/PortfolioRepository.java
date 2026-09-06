package com.moneykk.moneytown.analysis.ai.domain.repository;

import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
    Optional<Portfolio> findByIdAndIsDeletedIsFalse(UUID id);
}

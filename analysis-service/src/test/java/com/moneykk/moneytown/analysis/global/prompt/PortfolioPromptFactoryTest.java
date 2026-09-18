package com.moneykk.moneytown.analysis.global.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.analysis.ai.command.dto.PortfolioCandidate;
import com.moneykk.moneytown.analysis.ai.domain.AssetType;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioPromptFactoryTest {

    private final PortfolioPromptFactory factory = new PortfolioPromptFactory(new ObjectMapper().findAndRegisterModules());

    private PortfolioCandidate candidate() {
        return new PortfolioCandidate(
                UUID.randomUUID(), "테스트 공모", 10_000L, 100L, 50L,
                50, 1_000_000L, 2, 5, Instant.now().plusSeconds(86_400),
                "REAL_ESTATE", null, null, null
        );
    }

    @Test
    @DisplayName("system 프롬프트는 핵심 규칙 문구를 포함한다")
    void system_containsCoreRules() {
        String system = factory.system();

        assertThat(system).contains("offeringId").contains("percentage");
    }

    @Test
    @DisplayName("user 프롬프트는 투자 금액/위험 성향/후보 목록을 포함한다")
    void user_includesInvestmentAmountAndRiskType() {
        String user = factory.user(1_000_000L, RiskType.MEDIUM, AssetType.REAL_ESTATE, List.of(candidate()));

        assertThat(user).contains("1000000").contains("MEDIUM").contains("REAL_ESTATE").contains("테스트 공모");
    }

    @Test
    @DisplayName("선호 자산 유형이 없으면 '제한 없음'으로 표시한다")
    void user_nullPreferredAssetType_showsNoRestriction() {
        String user = factory.user(500_000L, RiskType.LOW, null, List.of(candidate()));

        assertThat(user).contains("제한 없음");
    }
}

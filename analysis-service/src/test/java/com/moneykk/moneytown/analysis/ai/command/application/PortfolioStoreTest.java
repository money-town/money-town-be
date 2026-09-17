package com.moneykk.moneytown.analysis.ai.command.application;

import com.moneykk.moneytown.analysis.ai.command.application.PortfolioStore;
import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;
import com.moneykk.moneytown.analysis.ai.domain.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioStoreTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    private PortfolioStore store;

    private final UUID userId = UUID.randomUUID();
    private final UUID idempotencyKey = UUID.randomUUID();
    private final UUID portfolioId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        store = new PortfolioStore(portfolioRepository);
    }

    private Portfolio portfolio() {
        return Portfolio.builder()
                .idempotencyKey(idempotencyKey)
                .userId(userId)
                .investmentAmount(1_000_000L)
                .riskType(RiskType.MEDIUM)
                .assetType(null)
                .model("gpt-4o-mini")
                .promptVersion("v1")
                .build();
    }

    @Test
    @DisplayName("findByUserIdAndIdempotencyKey는 리포지토리에 그대로 위임한다")
    void findByUserIdAndIdempotencyKey_delegates() {
        Portfolio p = portfolio();
        when(portfolioRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.of(p));

        assertThat(store.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).contains(p);
    }

    @Test
    @DisplayName("claim은 saveAndFlush로 위임한다")
    void claim_delegatesToSaveAndFlush() {
        Portfolio p = portfolio();
        when(portfolioRepository.saveAndFlush(p)).thenReturn(p);

        assertThat(store.claim(p)).isEqualTo(p);
        verify(portfolioRepository).saveAndFlush(p);
    }

    @Test
    @DisplayName("PROCESSING 상태에서 complete가 반영되면 true를 반환한다")
    void complete_updatedOne_returnsTrue() {
        when(portfolioRepository.completeIfProcessing(eq(portfolioId), any(), anyLong(), any()))
                .thenReturn(1);

        assertThat(store.complete(portfolioId, "{}", 100L)).isTrue();
    }

    @Test
    @DisplayName("이미 PROCESSING이 아니면 complete는 false를 반환한다")
    void complete_updatedZero_returnsFalse() {
        when(portfolioRepository.completeIfProcessing(eq(portfolioId), any(), anyLong(), any()))
                .thenReturn(0);

        assertThat(store.complete(portfolioId, "{}", 100L)).isFalse();
    }

    @Test
    @DisplayName("PROCESSING 상태에서 fail이 반영되면 true를 반환한다")
    void fail_updatedOne_returnsTrue() {
        when(portfolioRepository.failIfProcessing(eq(portfolioId), any(), anyLong(), any()))
                .thenReturn(1);

        assertThat(store.fail(portfolioId, "에러", 100L)).isTrue();
    }

    @Test
    @DisplayName("이미 PROCESSING이 아니면 fail은 false를 반환한다")
    void fail_updatedZero_returnsFalse() {
        when(portfolioRepository.failIfProcessing(eq(portfolioId), any(), anyLong(), any()))
                .thenReturn(0);

        assertThat(store.fail(portfolioId, "에러", 100L)).isFalse();
    }
}

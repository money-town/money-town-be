package com.moneykk.moneytown.analysis.ai.command.application;

import com.moneykk.moneytown.analysis.ai.command.application.PortfolioStore;
import com.moneykk.moneytown.analysis.ai.domain.AiStatus;
import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;
import com.moneykk.moneytown.analysis.ai.domain.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    @Test
    @DisplayName("PENDING/PROCESSING인 포트폴리오가 있으면 hasActivePortfolio는 true를 반환한다")
    void hasActivePortfolio_found_returnsTrue() {
        when(portfolioRepository.findFirstByUserIdAndStatusInAndIsDeleted(
                eq(userId), eq(List.of(AiStatus.PENDING, AiStatus.PROCESSING)), eq(false)
        )).thenReturn(Optional.of(portfolio()));

        assertThat(store.hasActivePortfolio(userId)).isTrue();
    }

    @Test
    @DisplayName("PENDING/PROCESSING인 포트폴리오가 없으면 hasActivePortfolio는 false를 반환한다")
    void hasActivePortfolio_notFound_returnsFalse() {
        when(portfolioRepository.findFirstByUserIdAndStatusInAndIsDeleted(
                eq(userId), eq(List.of(AiStatus.PENDING, AiStatus.PROCESSING)), eq(false)
        )).thenReturn(Optional.empty());

        assertThat(store.hasActivePortfolio(userId)).isFalse();
    }

    @Test
    @DisplayName("limit이 0 이하면 조회 없이 빈 리스트를 반환한다")
    void claimBatch_nonPositiveLimit_returnsEmptyWithoutQuerying() {
        assertThat(store.claimBatch(0)).isEmpty();

        verify(portfolioRepository, never()).findPendingIdsForUpdateSkipLocked(anyInt());
    }

    @Test
    @DisplayName("클레임할 PENDING이 없으면 markProcessing 없이 빈 리스트를 반환한다")
    void claimBatch_noPendingIds_returnsEmptyWithoutMarking() {
        when(portfolioRepository.findPendingIdsForUpdateSkipLocked(5)).thenReturn(List.of());

        assertThat(store.claimBatch(5)).isEmpty();

        verify(portfolioRepository, never()).markProcessing(any(), any());
    }

    @Test
    @DisplayName("클레임된 id들을 일괄로 PROCESSING 전환하고 그대로 반환한다")
    void claimBatch_foundIds_marksProcessingAndReturnsIds() {
        List<UUID> ids = List.of(portfolioId, UUID.randomUUID());
        when(portfolioRepository.findPendingIdsForUpdateSkipLocked(5)).thenReturn(ids);

        List<UUID> result = store.claimBatch(5);

        assertThat(result).isEqualTo(ids);
        verify(portfolioRepository).markProcessing(eq(ids), any());
    }
}

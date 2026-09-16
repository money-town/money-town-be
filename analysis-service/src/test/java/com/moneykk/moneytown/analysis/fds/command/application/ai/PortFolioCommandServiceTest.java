package com.moneykk.moneytown.analysis.fds.command.application.ai;

import com.moneykk.moneytown.analysis.ai.command.application.PortFolioCommandService;
import com.moneykk.moneytown.analysis.ai.command.application.PortfolioGenerator;
import com.moneykk.moneytown.analysis.ai.command.application.PortfolioStore;
import com.moneykk.moneytown.analysis.ai.command.dto.CreatePortfolioRequest;
import com.moneykk.moneytown.analysis.ai.command.dto.CreatePortfolioResponse;
import com.moneykk.moneytown.analysis.ai.command.dto.DeletePortfolioResponse;
import com.moneykk.moneytown.analysis.ai.domain.AiStatus;
import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;
import com.moneykk.moneytown.analysis.ai.domain.repository.PortfolioRepository;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortFolioCommandServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private PortfolioStore portfolioStore;
    @Mock
    private PortfolioGenerator portfolioGenerator;

    private PortFolioCommandService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID idempotencyKey = UUID.randomUUID();
    private final UUID portfolioId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PortFolioCommandService(portfolioRepository, portfolioStore, portfolioGenerator);
    }

    private CreatePortfolioRequest request() {
        return new CreatePortfolioRequest(1_000_000L, RiskType.MEDIUM, null);
    }

    private Portfolio portfolioWithId(UUID id) {
        Portfolio p = Portfolio.builder()
                .idempotencyKey(idempotencyKey)
                .userId(userId)
                .investmentAmount(1_000_000L)
                .riskType(RiskType.MEDIUM)
                .assetType(null)
                .model("gpt-4o-mini")
                .promptVersion("v1")
                .build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    // ===== createPortfolio =====

    @Test
    @DisplayName("이미 같은 멱등키로 생성된 포트폴리오가 있으면 그대로 반환하고 claim/generate는 호출하지 않는다")
    void createPortfolio_existingIdempotencyKey_returnsExistingWithoutClaiming() {
        Portfolio existing = portfolioWithId(portfolioId);
        when(portfolioStore.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.of(existing));

        CreatePortfolioResponse response = service.createPortfolio(userId, idempotencyKey, request());

        assertThat(response.portfolioId()).isEqualTo(portfolioId);
        verify(portfolioStore, never()).claim(any());
        verifyNoInteractions(portfolioGenerator);
    }

    @Test
    @DisplayName("새 요청이면 claim 후 비동기 생성에 위임하고 PROCESSING 응답을 반환한다")
    void createPortfolio_newRequest_claimsAndDelegatesGeneration() {
        Portfolio claimed = portfolioWithId(portfolioId);
        when(portfolioStore.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.empty());
        when(portfolioStore.claim(any(Portfolio.class))).thenReturn(claimed);

        CreatePortfolioResponse response = service.createPortfolio(userId, idempotencyKey, request());

        assertThat(response.portfolioId()).isEqualTo(portfolioId);
        assertThat(response.status()).isEqualTo(AiStatus.PROCESSING);
        verify(portfolioGenerator).generate(portfolioId);
    }

    @Test
    @DisplayName("동시 요청으로 claim이 제약 위반이면 재조회한 기존 결과를 반환한다 (멱등)")
    void createPortfolio_claimConflict_returnsExistingFromRelookup() {
        Portfolio existing = portfolioWithId(portfolioId);
        when(portfolioStore.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(portfolioStore.claim(any(Portfolio.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        CreatePortfolioResponse response = service.createPortfolio(userId, idempotencyKey, request());

        assertThat(response.portfolioId()).isEqualTo(portfolioId);
        verifyNoInteractions(portfolioGenerator);
    }

    @Test
    @DisplayName("claim이 제약 위반인데 재조회도 비어있으면 AI_PORTFOLIO_NOT_FOUND 예외를 던진다")
    void createPortfolio_claimConflictButRelookupEmpty_throwsNotFound() {
        when(portfolioStore.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.empty());
        when(portfolioStore.claim(any(Portfolio.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.createPortfolio(userId, idempotencyKey, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnalysisErrorCode.AI_PORTFOLIO_NOT_FOUND);
    }

    @Test
    @DisplayName("AI 처리 스레드풀이 포화되어 생성 위임이 거부되면 FAILED로 마감하고 AI_CAPACITY_EXCEEDED를 던진다")
    void createPortfolio_generateRejected_marksFailedAndThrowsCapacityExceeded() {
        Portfolio claimed = portfolioWithId(portfolioId);
        when(portfolioStore.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.empty());
        when(portfolioStore.claim(any(Portfolio.class))).thenReturn(claimed);
        doThrow(new RejectedExecutionException("queue full"))
                .when(portfolioGenerator).generate(portfolioId);

        assertThatThrownBy(() -> service.createPortfolio(userId, idempotencyKey, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnalysisErrorCode.AI_CAPACITY_EXCEEDED);

        verify(portfolioStore).fail(eq(portfolioId), any(), eq(0L));
    }

    // ===== deletePortfolio =====

    @Test
    @DisplayName("포트폴리오가 없으면 AI_PORTFOLIO_NOT_FOUND 예외를 던진다")
    void deletePortfolio_notFound_throwsNotFound() {
        when(portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePortfolio("USER", userId, portfolioId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnalysisErrorCode.AI_PORTFOLIO_NOT_FOUND);
    }

    @Test
    @DisplayName("소유자도 아니고 ADMIN도 아니면 AI_FORBIDDEN 예외를 던진다")
    void deletePortfolio_notOwnerAndNotAdmin_throwsForbidden() {
        Portfolio portfolio = portfolioWithId(portfolioId);
        when(portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)).thenReturn(Optional.of(portfolio));

        UUID otherUser = UUID.randomUUID();
        assertThatThrownBy(() -> service.deletePortfolio("USER", otherUser, portfolioId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnalysisErrorCode.AI_FORBIDDEN);
    }

    @Test
    @DisplayName("ADMIN이면 소유자가 아니어도 삭제할 수 있다")
    void deletePortfolio_adminNotOwner_succeeds() {
        Portfolio portfolio = portfolioWithId(portfolioId);
        portfolio.complete("{}", 100L); // PROCESSING -> COMPLETED로 전이시켜 삭제 가능 상태로 만든다
        when(portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)).thenReturn(Optional.of(portfolio));

        UUID adminUserId = UUID.randomUUID();
        DeletePortfolioResponse response = service.deletePortfolio("ADMIN", adminUserId, portfolioId);

        assertThat(response.portfolioId()).isEqualTo(portfolioId);
        assertThat(portfolio.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("PROCESSING 상태면 삭제할 수 없다")
    void deletePortfolio_processingStatus_throwsProcessingConflict() {
        Portfolio portfolio = portfolioWithId(portfolioId);
        // Portfolio.builder()로 만들면 초기 status가 PROCESSING이다.
        when(portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)).thenReturn(Optional.of(portfolio));

        assertThatThrownBy(() -> service.deletePortfolio("USER", userId, portfolioId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnalysisErrorCode.AI_PROCESSING);
    }

    @Test
    @DisplayName("소유자이고 PROCESSING이 아니면 정상적으로 소프트 삭제된다")
    void deletePortfolio_ownerAndNotProcessing_softDeletes() {
        Portfolio portfolio = portfolioWithId(portfolioId);
        portfolio.complete("{}", 100L); // PROCESSING -> COMPLETED로 전이시켜 삭제 가능 상태로 만든다
        when(portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)).thenReturn(Optional.of(portfolio));

        DeletePortfolioResponse response = service.deletePortfolio("USER", userId, portfolioId);

        assertThat(response.portfolioId()).isEqualTo(portfolioId);
        assertThat(portfolio.isDeleted()).isTrue();
    }
}

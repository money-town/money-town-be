package com.moneykk.moneytown.analysis.fds.command.application.fds;

import com.moneykk.moneytown.analysis.fds.command.application.FdsBlockApplier;
import com.moneykk.moneytown.analysis.fds.command.application.PreFdsCheckService;
import com.moneykk.moneytown.analysis.fds.command.dto.request.PreFdsCheckRequest;
import com.moneykk.moneytown.analysis.fds.command.dto.response.PreFdsCheckResult;
import com.moneykk.moneytown.analysis.fds.command.redis.FdsCheckIdempotencyStore;
import com.moneykk.moneytown.analysis.fds.command.redis.FdsCounts;
import com.moneykk.moneytown.analysis.fds.command.redis.FdsRedisCounter;
import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsUserStateRepository;
import com.moneykk.moneytown.analysis.global.config.FdsRuleProperties;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.analysis.notification.command.application.NotificationCommandService;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreFdsCheckServiceTest {

    @Mock
    private FdsCheckIdempotencyStore idempotencyStore;
    @Mock
    private FdsRedisCounter redisCounter;
    @Mock
    private FdsUserStateRepository fdsUserStateRepository;
    @Mock
    private FdsBlockApplier fdsBlockApplier;
    @Mock
    private NotificationCommandService notificationCommandService;

    private PreFdsCheckService service;

    private final UUID requestId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        FdsRuleProperties ruleProperties = new FdsRuleProperties(Map.of(
                RuleCode.RAPID_REQUEST, new FdsRuleProperties.Threshold(1, 5, 3),
                RuleCode.BURST_REQUEST, new FdsRuleProperties.Threshold(10, 20, 10),
                RuleCode.MULTI_OFFERING_BURST, new FdsRuleProperties.Threshold(10, 5, 3)
        ));
        service = new PreFdsCheckService(
                idempotencyStore, redisCounter, fdsUserStateRepository,
                ruleProperties, fdsBlockApplier, notificationCommandService
        );
    }

    private PreFdsCheckRequest request() {
        return new PreFdsCheckRequest(requestId, userId, assetId);
    }

    private void stubNormalUser() {
        when(fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());
    }

    private void stubBegin() {
        when(idempotencyStore.find(requestId)).thenReturn(Optional.empty());
        when(idempotencyStore.tryBegin(requestId)).thenReturn(true);
    }

    @Test
    @DisplayName("이미 완료된 requestId면 캐시된 결과를 즉시 반환하고 아무것도 재평가하지 않는다")
    void check_cacheHit_returnsCachedResultWithoutEvaluating() {
        PreFdsCheckResult cached = PreFdsCheckResult.pass();
        when(idempotencyStore.find(requestId)).thenReturn(Optional.of(cached));

        PreFdsCheckResult result = service.check(request());

        assertThat(result).isEqualTo(cached);
        verify(idempotencyStore, never()).tryBegin(any());
        verifyNoInteractions(redisCounter, fdsUserStateRepository, fdsBlockApplier);
    }

    @Test
    @DisplayName("다른 요청이 이미 선점 중이면 재조회한 결과를 반환한다")
    void check_concurrentBegin_returnsRelookupResult() {
        PreFdsCheckResult existing = PreFdsCheckResult.pass();
        when(idempotencyStore.find(requestId)).thenReturn(Optional.empty(), Optional.of(existing));
        when(idempotencyStore.tryBegin(requestId)).thenReturn(false);

        PreFdsCheckResult result = service.check(request());

        assertThat(result).isEqualTo(existing);
        verifyNoInteractions(redisCounter, fdsBlockApplier);
    }

    @Test
    @DisplayName("선점 실패 후 재조회도 비어있으면 FDS_UNAVAILABLE 예외를 던지고 마커를 정리한다")
    void check_concurrentBeginAndRelookupEmpty_throwsUnavailable() {
        when(idempotencyStore.find(requestId)).thenReturn(Optional.empty(), Optional.empty());
        when(idempotencyStore.tryBegin(requestId)).thenReturn(false);

        assertThatThrownBy(() -> service.check(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnalysisErrorCode.FDS_UNAVAILABLE);

        verify(idempotencyStore).abort(requestId);
    }

    @Test
    @DisplayName("이미 BLOCKED 상태면 카운팅 없이 즉시 차단 결과를 반환한다")
    void check_userAlreadyBlocked_returnsBlockWithoutCounting() {
        stubBegin();
        FdsUserState blocked = FdsUserState.create(userId);
        blocked.block("PRIOR_VIOLATION");
        when(fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(blocked));

        PreFdsCheckResult result = service.check(request());

        assertThat(result.result()).isEqualTo("BLOCK");
        assertThat(result.ruleCode()).isNull();
        verifyNoInteractions(redisCounter, fdsBlockApplier);
        verify(idempotencyStore).complete(eq(requestId), eq(result));
    }

    @Test
    @DisplayName("모든 룰이 임계치 미만이면 PASS를 반환한다")
    void check_noViolation_returnsPass() {
        stubBegin();
        stubNormalUser();
        when(redisCounter.recordAndCount(userId, requestId, assetId))
                .thenReturn(new FdsCounts(1, 1, 1));

        PreFdsCheckResult result = service.check(request());

        assertThat(result).isEqualTo(PreFdsCheckResult.pass());
        verifyNoInteractions(fdsBlockApplier, notificationCommandService);
    }

    @Test
    @DisplayName("RAPID_REQUEST 임계치를 넘기면 해당 룰로 차단한다")
    void check_rapidViolation_blocks() {
        stubBegin();
        stubNormalUser();
        when(redisCounter.recordAndCount(userId, requestId, assetId))
                .thenReturn(new FdsCounts(5, 0, 0));

        PreFdsCheckResult result = service.check(request());

        assertThat(result.ruleCode()).isEqualTo(RuleCode.RAPID_REQUEST);
        verify(fdsBlockApplier).applyBlock(userId, requestId, assetId, RuleCode.RAPID_REQUEST, 5, 5);
    }

    @Test
    @DisplayName("RAPID_REQUEST는 통과하고 BURST_REQUEST만 위반하면 BURST_REQUEST로 차단한다")
    void check_burstViolationOnly_blocks() {
        stubBegin();
        stubNormalUser();
        when(redisCounter.recordAndCount(userId, requestId, assetId))
                .thenReturn(new FdsCounts(1, 20, 0));

        PreFdsCheckResult result = service.check(request());

        assertThat(result.ruleCode()).isEqualTo(RuleCode.BURST_REQUEST);
    }

    @Test
    @DisplayName("RAPID/BURST는 통과하고 MULTI_OFFERING_BURST만 위반하면 그 룰로 차단한다")
    void check_multiOfferingViolationOnly_blocks() {
        stubBegin();
        stubNormalUser();
        when(redisCounter.recordAndCount(userId, requestId, assetId))
                .thenReturn(new FdsCounts(1, 1, 5));

        PreFdsCheckResult result = service.check(request());

        assertThat(result.ruleCode()).isEqualTo(RuleCode.MULTI_OFFERING_BURST);
    }

    @Test
    @DisplayName("SUSPICIOUS 상태면 NORMAL보다 낮은 임계치로 차단한다")
    void check_suspiciousStatus_usesLowerThreshold() {
        stubBegin();
        FdsUserState suspicious = FdsUserState.create(userId);
        suspicious.markSuspicious();
        when(fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(suspicious));
        when(redisCounter.recordAndCount(userId, requestId, assetId))
                .thenReturn(new FdsCounts(3, 0, 0));

        PreFdsCheckResult result = service.check(request());

        assertThat(result.ruleCode()).isEqualTo(RuleCode.RAPID_REQUEST);
        verify(fdsBlockApplier).applyBlock(userId, requestId, assetId, RuleCode.RAPID_REQUEST, 3, 3);
    }

    @Test
    @DisplayName("차단 알림 발송이 실패해도 검사 결과와 멱등 저장에는 영향이 없다")
    void check_blockNotificationFails_resultStillBlocked() {
        stubBegin();
        stubNormalUser();
        when(redisCounter.recordAndCount(userId, requestId, assetId))
                .thenReturn(new FdsCounts(5, 0, 0));
        when(notificationCommandService.send(any(), any())).thenThrow(new RuntimeException("slack down"));

        PreFdsCheckResult result = service.check(request());

        assertThat(result.ruleCode()).isEqualTo(RuleCode.RAPID_REQUEST);
        verify(idempotencyStore).complete(eq(requestId), eq(result));
    }

    @Test
    @DisplayName("차단 반영 중 BusinessException이 나면 마커를 정리하고 그대로 재던진다")
    void check_applyBlockThrowsBusinessException_abortsAndRethrows() {
        stubBegin();
        stubNormalUser();
        when(redisCounter.recordAndCount(userId, requestId, assetId))
                .thenReturn(new FdsCounts(5, 0, 0));
        doThrow(new BusinessException(AnalysisErrorCode.FDS_ALREADY_BLOCKED))
                .when(fdsBlockApplier).applyBlock(any(), any(), any(), any(), anyLong(), anyInt());

        assertThatThrownBy(() -> service.check(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnalysisErrorCode.FDS_ALREADY_BLOCKED);

        verify(idempotencyStore).abort(requestId);
        verify(idempotencyStore, never()).complete(any(), any());
    }

    @Test
    @DisplayName("Redis 카운터에서 예상 못한 예외가 나면 FDS_UNAVAILABLE로 감싸서 던진다")
    void check_redisFailure_wrapsAsFdsUnavailable() {
        stubBegin();
        stubNormalUser();
        when(redisCounter.recordAndCount(userId, requestId, assetId))
                .thenThrow(new RuntimeException("redis timeout"));

        assertThatThrownBy(() -> service.check(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnalysisErrorCode.FDS_UNAVAILABLE);

        verify(idempotencyStore).abort(requestId);
    }
}

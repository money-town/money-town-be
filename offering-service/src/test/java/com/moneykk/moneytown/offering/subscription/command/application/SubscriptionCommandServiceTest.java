package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.command.dto.request.SubscriptionCreateRequest;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.domain.service.SubscriptionRequestHasher;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.AnalysisServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.UserServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.UserInvestmentEligibilityResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class SubscriptionCommandServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionIdempotencyService subscriptionIdempotencyService;

    @Mock
    private SubscriptionTransactionService subscriptionTransactionService;

    @Mock
    private SubscriptionLimitExceededEventService subscriptionLimitExceededEventService;

    @Mock
    private IdempotencyRequestRepository idempotencyRequestRepository;

    @Mock
    private SubscriptionRequestHasher subscriptionRequestHasher;

    @Mock
    private AnalysisServiceClient analysisServiceClient;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private SubscriptionCommandService subscriptionCommandService;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("유효하지 않은 Correlation-ID는 멱등 요청을 선점하기 전에 거부한다")
    void rejectsInvalidCorrelationIdBeforeBeginningIdempotency(
            String correlationId
    ) {
        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "test-idempotency-key",
                        request,
                        correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
                );

        // 핵심 검증: 멱등 요청 PROCESSING 행을 생성하지 않아야 한다.
        verifyNoInteractions(subscriptionIdempotencyService);

        verifyNoInteractions(subscriptionRequestHasher);
        verifyNoInteractions(subscriptionTransactionService);
        verifyNoInteractions(offeringRepository);
        verifyNoInteractions(subscriptionRepository);
        verifyNoInteractions(idempotencyRequestRepository);
        verifyNoInteractions(analysisServiceClient);
        verifyNoInteractions(userServiceClient);
    }

    @Test
    @DisplayName("최대 청약 수량을 초과하면 한도 초과 이벤트를 기록하고 청약 생성을 중단한다")
    void recordsLimitExceededBeforePreFdsAndSubscriptionCreation() {
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        String idempotencyKey = "limit-exceeded-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        long requestedQuantity = 101L;
        long maxSubscriptionQuantity = 100L;

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(requestedQuantity);

        UserInvestmentEligibilityResponse user =
                new UserInvestmentEligibilityResponse(
                        userId,
                        "ACTIVE",
                        "VERIFIED",
                        Instant.now().plusSeconds(3_600)
                );

        Offering offering = mock(Offering.class);

        when(subscriptionRequestHasher.hash(
                offeringId,
                requestedQuantity
        )).thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class),
                eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey),
                eq(requestHash),
                eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(
                        ApiResponse.success(
                                user,
                                "사용자 조회 성공"
                        )
                );

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        Instant now = Instant.now();

        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.OPEN);
        when(offering.getStartAt())
                .thenReturn(now.minusSeconds(60));
        when(offering.getEndAt())
                .thenReturn(now.plusSeconds(3_600));
        when(offering.getMinSubscriptionQuantity())
                .thenReturn(1L);
        when(offering.getMaxSubscriptionQuantity())
                .thenReturn(maxSubscriptionQuantity);
        when(offering.getAssetId())
                .thenReturn(assetId);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId,
                        userId,
                        idempotencyKey,
                        request,
                        correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED
                );

        /*
         * tryBegin()에 사용한 idempotencyRequestId가
         * 한도 초과 이벤트의 aggregateId로 전달되는지 검증한다.
         */
        ArgumentCaptor<UUID> idempotencyRequestIdCaptor =
                ArgumentCaptor.forClass(UUID.class);

        verify(subscriptionIdempotencyService).tryBegin(
                idempotencyRequestIdCaptor.capture(),
                eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey),
                eq(requestHash),
                eq("SUBSCRIPTION")
        );

        verify(subscriptionLimitExceededEventService)
                .recordLimitExceeded(
                        idempotencyRequestIdCaptor.getValue(),
                        userId,
                        idempotencyKey,
                        assetId,
                        requestedQuantity,
                        maxSubscriptionQuantity,
                        correlationId
                );

        // 한도 초과 이벤트 처리에서 이미 FAILED로 변경했으므로 중복 처리하지 않는다.
        verify(subscriptionIdempotencyService, never()).fail(
                any(UUID.class),
                anyString(),
                anyString(),
                anyInt()
        );

        // 한도 초과 요청은 PreFDS와 실제 청약 생성까지 진행하지 않는다.
        verifyNoInteractions(analysisServiceClient);
        verifyNoInteractions(subscriptionTransactionService);
    }
}
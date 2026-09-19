package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.command.dto.request.SubscriptionCreateRequest;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCreateResponse;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCreateResult;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyRequest;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyRequestStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.domain.service.SubscriptionRequestHasher;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.AnalysisServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.UserServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.PreFdsCheckResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.UserInvestmentEligibilityResponse;
import feign.FeignException;
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
import static org.mockito.Mockito.lenient;
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
                        "INVESTOR",
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

    @Test
    @DisplayName("User 응답에 userRole이 없으면 외부 응답 오류로 처리한다")
    void rejectsUserResponseWithoutRole() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "missing-user-role-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        UserInvestmentEligibilityResponse user =
                new UserInvestmentEligibilityResponse(
                        userId,
                        null,
                        "ACTIVE",
                        "VERIFIED",
                        Instant.now().plusSeconds(3_600)
                );

        when(subscriptionRequestHasher.hash(
                offeringId,
                request.quantity()
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

        // when
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

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
                );

        verify(subscriptionIdempotencyService).fail(
                userId,
                IdempotencyOperation.CREATE_SUBSCRIPTION.name(),
                idempotencyKey,
                SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
                        .getStatus()
                        .value()
        );

        verifyNoInteractions(offeringRepository);
        verifyNoInteractions(analysisServiceClient);
        verifyNoInteractions(subscriptionTransactionService);
        verifyNoInteractions(subscriptionLimitExceededEventService);
    }

    @Test
    @DisplayName("INVESTOR가 아닌 사용자는 청약 자격 미충족으로 처리한다")
    void rejectsNonInvestorUser() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "non-investor-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        UserInvestmentEligibilityResponse user =
                new UserInvestmentEligibilityResponse(
                        userId,
                        "ISSUER",
                        "ACTIVE",
                        "VERIFIED",
                        Instant.now().plusSeconds(3_600)
                );

        when(subscriptionRequestHasher.hash(
                offeringId,
                request.quantity()
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

        // when
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

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.SUBSCRIPTION_ELIGIBILITY_NOT_MET
                );

        verify(subscriptionIdempotencyService).fail(
                userId,
                IdempotencyOperation.CREATE_SUBSCRIPTION.name(),
                idempotencyKey,
                SubscriptionErrorCode.SUBSCRIPTION_ELIGIBILITY_NOT_MET
                        .getStatus()
                        .value()
        );

        verifyNoInteractions(offeringRepository);
        verifyNoInteractions(analysisServiceClient);
        verifyNoInteractions(subscriptionTransactionService);
        verifyNoInteractions(subscriptionLimitExceededEventService);
    }

    private UserInvestmentEligibilityResponse eligibleUser(UUID userId) {
        return new UserInvestmentEligibilityResponse(
                userId,
                "INVESTOR",
                "ACTIVE",
                "VERIFIED",
                Instant.now().plusSeconds(3_600)
        );
    }

    private Offering openOffering(
            UUID offeringId,
            UUID assetId,
            long minSubscriptionQuantity,
            long maxSubscriptionQuantity
    ) {
        Offering offering = mock(Offering.class);
        Instant now = Instant.now();

        lenient().when(offering.getOfferingId()).thenReturn(offeringId);
        lenient().when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.OPEN);
        lenient().when(offering.getStartAt())
                .thenReturn(now.minusSeconds(60));
        lenient().when(offering.getEndAt())
                .thenReturn(now.plusSeconds(3_600));
        lenient().when(offering.getMinSubscriptionQuantity())
                .thenReturn(minSubscriptionQuantity);
        lenient().when(offering.getMaxSubscriptionQuantity())
                .thenReturn(maxSubscriptionQuantity);
        lenient().when(offering.getAssetId()).thenReturn(assetId);

        return offering;
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("유효하지 않은 Idempotency-Key는 멱등 요청을 선점하기 전에 거부한다")
    void rejectsInvalidIdempotencyKeyBeforeBeginningIdempotency(
            String idempotencyKey
    ) {
        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        idempotencyKey,
                        request,
                        UUID.randomUUID().toString()
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.INVALID_IDEMPOTENCY_KEY);

        verifyNoInteractions(subscriptionIdempotencyService);
        verifyNoInteractions(subscriptionRequestHasher);
        verifyNoInteractions(offeringRepository);
    }

    @Test
    @DisplayName("Idempotency-Key가 100자를 초과하면 청약 생성을 거부한다")
    void rejectsIdempotencyKeyLongerThan100Characters() {
        String idempotencyKey = "a".repeat(101);
        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        idempotencyKey,
                        request,
                        UUID.randomUUID().toString()
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.INVALID_IDEMPOTENCY_KEY);

        verifyNoInteractions(subscriptionIdempotencyService);
    }

    @Test
    @DisplayName("신규 청약 요청을 정상적으로 생성한다")
    void createsNewSubscriptionSuccessfully() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        String idempotencyKey = "new-subscription-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";
        long quantity = 10L;
        long pricePerUnit = 1_000L;

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(quantity);

        Offering offering = openOffering(offeringId, assetId, 1L, 100L);
        when(offering.getPricePerUnit()).thenReturn(pricePerUnit);

        when(subscriptionRequestHasher.hash(offeringId, quantity))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class),
                eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey),
                eq(requestHash),
                eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(ApiResponse.success(
                        eligibleUser(userId), "사용자 조회 성공"
                ));

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(analysisServiceClient.check(any()))
                .thenReturn(ApiResponse.success(
                        new PreFdsCheckResponse("PASS", null),
                        "FDS 검사 통과"
                ));

        SubscriptionCreateResponse serviceResponse =
                new SubscriptionCreateResponse(
                        subscriptionId,
                        offeringId,
                        quantity,
                        pricePerUnit,
                        quantity * pricePerUnit,
                        SubscriptionStatus.PROCESSING
                );

        when(subscriptionTransactionService.createSubscription(
                offeringId,
                userId,
                idempotencyKey,
                quantity,
                pricePerUnit,
                correlationId
        )).thenReturn(serviceResponse);

        // when
        SubscriptionCreateResult result = subscriptionCommandService.create(
                offeringId, userId, idempotencyKey, request, correlationId
        );

        // then
        assertThat(result.replayed()).isFalse();
        assertThat(result.response()).isEqualTo(serviceResponse);

        verify(subscriptionIdempotencyService, never()).fail(
                any(UUID.class), anyString(), anyString(), anyInt()
        );
    }

    @Test
    @DisplayName("동일 Idempotency-Key로 다른 요청 내용이 오면 충돌로 처리한다")
    void rejectsConflictingRequestWithSameIdempotencyKey() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "conflict-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        when(subscriptionRequestHasher.hash(offeringId, 10L))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(0);

        IdempotencyRequest existing = mock(IdempotencyRequest.class);
        when(existing.getRequestHash()).thenReturn("other-hash");

        when(idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        userId,
                        IdempotencyOperation.CREATE_SUBSCRIPTION,
                        idempotencyKey
                ))
                .thenReturn(Optional.of(existing));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.IDEMPOTENCY_KEY_CONFLICT);

        verifyNoInteractions(offeringRepository);
        verifyNoInteractions(subscriptionTransactionService);
    }

    @Test
    @DisplayName("이미 완료된 동일 요청은 기존 청약 결과를 그대로 재사용한다")
    void replaysCompletedRequest() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        String idempotencyKey = "completed-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        when(subscriptionRequestHasher.hash(offeringId, 10L))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(0);

        IdempotencyRequest existing = mock(IdempotencyRequest.class);
        when(existing.getRequestHash()).thenReturn(requestHash);
        when(existing.getIdempotencyRequestStatus())
                .thenReturn(IdempotencyRequestStatus.COMPLETED);
        when(existing.getResourceId()).thenReturn(subscriptionId);

        when(idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        userId,
                        IdempotencyOperation.CREATE_SUBSCRIPTION,
                        idempotencyKey
                ))
                .thenReturn(Optional.of(existing));

        Subscription subscription = mock(Subscription.class);
        when(subscription.getSubscriptionId()).thenReturn(subscriptionId);
        when(subscription.getOfferingId()).thenReturn(offeringId);
        when(subscription.getQuantity()).thenReturn(10L);
        when(subscription.getPricePerUnit()).thenReturn(1_000L);
        when(subscription.getAmount()).thenReturn(10_000L);
        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.PROCESSING);

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        // when
        SubscriptionCreateResult result = subscriptionCommandService.create(
                offeringId, userId, idempotencyKey, request, correlationId
        );

        // then
        assertThat(result.replayed()).isTrue();
        assertThat(result.response())
                .isEqualTo(SubscriptionCreateResponse.from(subscription));

        verifyNoInteractions(subscriptionTransactionService);
    }

    @Test
    @DisplayName("완료된 요청에 연결된 리소스ID가 없으면 상태 오류로 처리한다")
    void rejectsCompletedRequestWithoutResourceId() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "completed-without-resource-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        when(subscriptionRequestHasher.hash(offeringId, 10L))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(0);

        IdempotencyRequest existing = mock(IdempotencyRequest.class);
        when(existing.getRequestHash()).thenReturn(requestHash);
        when(existing.getIdempotencyRequestStatus())
                .thenReturn(IdempotencyRequestStatus.COMPLETED);
        when(existing.getResourceId()).thenReturn(null);

        when(idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        userId,
                        IdempotencyOperation.CREATE_SUBSCRIPTION,
                        idempotencyKey
                ))
                .thenReturn(Optional.of(existing));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.IDEMPOTENCY_REQUEST_STATE_INVALID);

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("여전히 처리 중인 동일 요청은 중복 실행하지 않는다")
    void rejectsProcessingRequest() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "processing-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        when(subscriptionRequestHasher.hash(offeringId, 10L))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(0);

        IdempotencyRequest existing = mock(IdempotencyRequest.class);
        when(existing.getRequestHash()).thenReturn(requestHash);
        when(existing.getIdempotencyRequestStatus())
                .thenReturn(IdempotencyRequestStatus.PROCESSING);

        when(idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        userId,
                        IdempotencyOperation.CREATE_SUBSCRIPTION,
                        idempotencyKey
                ))
                .thenReturn(Optional.of(existing));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.IDEMPOTENCY_REQUEST_PROCESSING);
    }

    @Test
    @DisplayName("이전에 실패한 동일 요청은 재시도를 거부한다")
    void rejectsFailedRequest() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "failed-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        when(subscriptionRequestHasher.hash(offeringId, 10L))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(0);

        IdempotencyRequest existing = mock(IdempotencyRequest.class);
        when(existing.getRequestHash()).thenReturn(requestHash);
        when(existing.getIdempotencyRequestStatus())
                .thenReturn(IdempotencyRequestStatus.FAILED);

        when(idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        userId,
                        IdempotencyOperation.CREATE_SUBSCRIPTION,
                        idempotencyKey
                ))
                .thenReturn(Optional.of(existing));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.IDEMPOTENCY_REQUEST_FAILED);
    }

    @Test
    @DisplayName("동일 Idempotency-Key 요청 기록을 찾을 수 없으면 상태 오류로 처리한다")
    void rejectsWhenExistingIdempotencyRequestNotFound() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "missing-record-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        when(subscriptionRequestHasher.hash(offeringId, 10L))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(0);

        when(idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        userId,
                        IdempotencyOperation.CREATE_SUBSCRIPTION,
                        idempotencyKey
                ))
                .thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.IDEMPOTENCY_REQUEST_STATE_INVALID);
    }

    @Test
    @DisplayName("청약 대상 공모를 찾을 수 없으면 청약 생성을 거부한다")
    void rejectsWhenOfferingNotFound() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "offering-not-found-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        when(subscriptionRequestHasher.hash(offeringId, 10L))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(ApiResponse.success(
                        eligibleUser(userId), "사용자 조회 성공"
                ));

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_NOT_FOUND);

        verify(subscriptionIdempotencyService).fail(
                userId,
                IdempotencyOperation.CREATE_SUBSCRIPTION.name(),
                idempotencyKey,
                OfferingErrorCode.OFFERING_NOT_FOUND.getStatus().value()
        );

        verifyNoInteractions(analysisServiceClient);
        verifyNoInteractions(subscriptionTransactionService);
    }

    @Test
    @DisplayName("OPEN 상태가 아닌 공모는 청약을 거부한다")
    void rejectsSubscriptionWhenOfferingIsNotOpen() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        String idempotencyKey = "not-open-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        Offering offering = mock(Offering.class);
        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.SCHEDULED);

        when(subscriptionRequestHasher.hash(offeringId, 10L))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(ApiResponse.success(
                        eligibleUser(userId), "사용자 조회 성공"
                ));

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.SUBSCRIPTION_NOT_AVAILABLE);

        verifyNoInteractions(analysisServiceClient);
        verifyNoInteractions(subscriptionTransactionService);
    }

    @Test
    @DisplayName("모집 기간이 아직 시작되지 않은 공모는 청약을 거부한다")
    void rejectsSubscriptionBeforeOfferingStarts() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "before-start-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        Offering offering = mock(Offering.class);
        Instant now = Instant.now();
        when(offering.getOfferingStatus()).thenReturn(OfferingStatus.OPEN);
        when(offering.getStartAt()).thenReturn(now.plusSeconds(3_600));

        when(subscriptionRequestHasher.hash(offeringId, 10L))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(ApiResponse.success(
                        eligibleUser(userId), "사용자 조회 성공"
                ));

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.SUBSCRIPTION_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("모집 기간이 종료된 공모는 청약을 거부한다")
    void rejectsSubscriptionAfterOfferingEnds() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "after-end-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        Offering offering = mock(Offering.class);
        Instant now = Instant.now();
        when(offering.getOfferingStatus()).thenReturn(OfferingStatus.OPEN);
        when(offering.getStartAt()).thenReturn(now.minusSeconds(7_200));
        when(offering.getEndAt()).thenReturn(now.minusSeconds(3_600));

        when(subscriptionRequestHasher.hash(offeringId, 10L))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(ApiResponse.success(
                        eligibleUser(userId), "사용자 조회 성공"
                ));

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.SUBSCRIPTION_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("최소 청약 수량 미달이면 청약을 거부한다")
    void rejectsSubscriptionBelowMinimumQuantity() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        String idempotencyKey = "below-minimum-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";
        long quantity = 1L;

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(quantity);

        Offering offering = openOffering(offeringId, assetId, 10L, 100L);

        when(subscriptionRequestHasher.hash(offeringId, quantity))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(ApiResponse.success(
                        eligibleUser(userId), "사용자 조회 성공"
                ));

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.INVALID_SUBSCRIPTION_QUANTITY);

        verifyNoInteractions(analysisServiceClient);
    }

    @Test
    @DisplayName("Pre-FDS가 이상 거래로 판단하면 청약을 거부한다")
    void rejectsSubscriptionWhenPreFdsBlocks() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        String idempotencyKey = "fds-block-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";
        long quantity = 10L;

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(quantity);

        Offering offering = openOffering(offeringId, assetId, 1L, 100L);

        when(subscriptionRequestHasher.hash(offeringId, quantity))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(ApiResponse.success(
                        eligibleUser(userId), "사용자 조회 성공"
                ));

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(analysisServiceClient.check(any()))
                .thenReturn(ApiResponse.success(
                        new PreFdsCheckResponse("BLOCK", "RULE_001"),
                        "이상 거래 탐지"
                ));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.SUBSCRIPTION_BLOCKED_BY_FDS);

        verifyNoInteractions(subscriptionTransactionService);
    }

    @Test
    @DisplayName("Pre-FDS 응답이 PASS/BLOCK이 아니면 외부 응답 오류로 처리한다")
    void rejectsSubscriptionWhenPreFdsResultIsUnknown() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        String idempotencyKey = "fds-unknown-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";
        long quantity = 10L;

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(quantity);

        Offering offering = openOffering(offeringId, assetId, 1L, 100L);

        when(subscriptionRequestHasher.hash(offeringId, quantity))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(ApiResponse.success(
                        eligibleUser(userId), "사용자 조회 성공"
                ));

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(analysisServiceClient.check(any()))
                .thenReturn(ApiResponse.success(
                        new PreFdsCheckResponse("UNKNOWN", null),
                        "알 수 없는 결과"
                ));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("Pre-FDS 응답 데이터가 없으면 외부 응답 오류로 처리한다")
    void rejectsSubscriptionWhenPreFdsResponseIsMissing() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        String idempotencyKey = "fds-missing-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";
        long quantity = 10L;

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(quantity);

        Offering offering = openOffering(offeringId, assetId, 1L, 100L);

        when(subscriptionRequestHasher.hash(offeringId, quantity))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(ApiResponse.success(
                        eligibleUser(userId), "사용자 조회 성공"
                ));

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(analysisServiceClient.check(any()))
                .thenReturn(ApiResponse.success(null, "데이터 없음"));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("Pre-FDS 호출이 실패하면 청약 검증 서비스 오류로 처리한다")
    void rejectsSubscriptionWhenPreFdsCallFails() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        String idempotencyKey = "fds-unavailable-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";
        long quantity = 10L;

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(quantity);

        Offering offering = openOffering(offeringId, assetId, 1L, 100L);

        when(subscriptionRequestHasher.hash(offeringId, quantity))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(ApiResponse.success(
                        eligibleUser(userId), "사용자 조회 성공"
                ));

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(analysisServiceClient.check(any()))
                .thenThrow(mock(FeignException.class));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.FDS_SERVICE_UNAVAILABLE);

        verifyNoInteractions(subscriptionTransactionService);
    }

    @Test
    @DisplayName("User Service에서 사용자를 찾을 수 없으면 사용자 없음 오류로 처리한다")
    void rejectsSubscriptionWhenUserNotFound() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "user-not-found-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        when(subscriptionRequestHasher.hash(offeringId, 10L))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenThrow(mock(FeignException.NotFound.class));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(offeringRepository);
    }

    @Test
    @DisplayName("User Service 호출이 실패하면 사용자 상태 조회 서비스 오류로 처리한다")
    void rejectsSubscriptionWhenUserServiceCallFails() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "user-service-unavailable-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        when(subscriptionRequestHasher.hash(offeringId, 10L))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenThrow(mock(FeignException.class));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.USER_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("User 응답 데이터가 없으면 외부 응답 오류로 처리한다")
    void rejectsSubscriptionWhenUserResponseDataIsMissing() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "user-data-missing-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        when(subscriptionRequestHasher.hash(offeringId, 10L))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(ApiResponse.success(null, "데이터 없음"));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("User 응답의 사용자 ID가 요청 사용자와 다르면 외부 응답 오류로 처리한다")
    void rejectsSubscriptionWhenUserIdMismatches() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "user-id-mismatch-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        UserInvestmentEligibilityResponse otherUser =
                new UserInvestmentEligibilityResponse(
                        UUID.randomUUID(),
                        "INVESTOR",
                        "ACTIVE",
                        "VERIFIED",
                        Instant.now().plusSeconds(3_600)
                );

        when(subscriptionRequestHasher.hash(offeringId, 10L))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(ApiResponse.success(otherUser, "사용자 조회 성공"));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("예상하지 못한 런타임 예외가 발생하면 멱등 요청을 실패로 기록하고 예외를 다시 던진다")
    void recordsFailureAndRethrowsOnUnexpectedRuntimeException() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        String idempotencyKey = "unexpected-error-key";
        String correlationId = UUID.randomUUID().toString();
        String requestHash = "request-hash";
        long quantity = 10L;
        long pricePerUnit = 1_000L;

        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(quantity);

        Offering offering = openOffering(offeringId, assetId, 1L, 100L);
        when(offering.getPricePerUnit()).thenReturn(pricePerUnit);

        when(subscriptionRequestHasher.hash(offeringId, quantity))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(userId),
                eq(IdempotencyOperation.CREATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq("SUBSCRIPTION")
        )).thenReturn(1);

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(ApiResponse.success(
                        eligibleUser(userId), "사용자 조회 성공"
                ));

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(analysisServiceClient.check(any()))
                .thenReturn(ApiResponse.success(
                        new PreFdsCheckResponse("PASS", null),
                        "FDS 검사 통과"
                ));

        RuntimeException unexpected = new RuntimeException("boom");

        when(subscriptionTransactionService.createSubscription(
                offeringId, userId, idempotencyKey, quantity,
                pricePerUnit, correlationId
        )).thenThrow(unexpected);

        // when & then
        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> subscriptionCommandService.create(
                        offeringId, userId, idempotencyKey, request, correlationId
                )
        );

        assertThat(thrown).isSameAs(unexpected);

        verify(subscriptionIdempotencyService).fail(
                userId,
                IdempotencyOperation.CREATE_SUBSCRIPTION.name(),
                idempotencyKey,
                500
        );
    }
}
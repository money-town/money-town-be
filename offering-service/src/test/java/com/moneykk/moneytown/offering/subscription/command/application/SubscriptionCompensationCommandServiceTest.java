package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCompensationResponse;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCompensationResult;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyRequest;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyRequestStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.domain.service.SubscriptionRequestHasher;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.HoldingServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.HoldingSubscriptionStatusResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.WalletHoldStatus;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.WalletHoldStatusResponse;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionCompensationCommandServiceTest {

    private static final String RESOURCE_TYPE = "SUBSCRIPTION";
    private static final String SYSTEM_ROLE = "SYSTEM";

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private IdempotencyRequestRepository idempotencyRequestRepository;

    @Mock
    private SubscriptionIdempotencyService subscriptionIdempotencyService;

    @Mock
    private SubscriptionCompensationTransactionService
            subscriptionCompensationTransactionService;

    @Mock
    private SubscriptionRequestHasher subscriptionRequestHasher;

    @Mock
    private WalletServiceClient walletServiceClient;

    @Mock
    private HoldingServiceClient holdingServiceClient;

    @InjectMocks
    private SubscriptionCompensationCommandService service;

    @Test
    @DisplayName("MANUAL_REVIEW 청약의 외부 상태를 조회하고 보상 처리를 시작한다")
    void compensatesManualReviewSubscription() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "compensation-key";
        String correlationId = "correlation-id";
        String requestHash = "request-hash";

        Subscription subscription = mock(Subscription.class);

        WalletHoldStatusResponse walletStatus =
                walletStatus(subscriptionId);

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        SubscriptionCompensationResponse response =
                new SubscriptionCompensationResponse(
                        subscriptionId,
                        SubscriptionStatus.COMPENSATING
                );

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.MANUAL_REVIEW);

        when(walletServiceClient.getWalletHoldStatus(subscriptionId))
                .thenReturn(
                        ApiResponse.success(
                                walletStatus,
                                "Wallet Hold 조회 성공"
                        )
                );

        when(holdingServiceClient.getHoldingSubscriptionStatus(
                SYSTEM_ROLE,
                subscriptionId
        )).thenReturn(
                ApiResponse.success(
                        holdingStatus,
                        "Holding 처리 상태 조회 성공"
                )
        );

        when(subscriptionCompensationTransactionService.compensate(
                subscriptionId,
                adminId,
                idempotencyKey,
                correlationId,
                walletStatus,
                holdingStatus
        )).thenReturn(response);

        // when
        SubscriptionCompensationResult result =
                service.compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId
                );

        // then
        assertThat(result.response()).isEqualTo(response);
        assertThat(result.replayed()).isFalse();

        verify(walletServiceClient)
                .getWalletHoldStatus(subscriptionId);

        verify(holdingServiceClient)
                .getHoldingSubscriptionStatus(
                        SYSTEM_ROLE,
                        subscriptionId
                );

        verify(subscriptionCompensationTransactionService)
                .compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId,
                        walletStatus,
                        holdingStatus
                );

        verify(subscriptionIdempotencyService, never())
                .fail(
                        any(UUID.class),
                        any(String.class),
                        any(String.class),
                        any(Integer.class)
                );
    }

    @Test
    @DisplayName("Wallet Hold가 없다는 404 응답은 보상 가능한 상태로 처리한다")
    void treatsWalletNotFoundAsNoHold() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "wallet-not-found-key";
        String correlationId = "correlation-id";
        String requestHash = "request-hash";

        Subscription subscription = mock(Subscription.class);

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        SubscriptionCompensationResponse response =
                new SubscriptionCompensationResponse(
                        subscriptionId,
                        SubscriptionStatus.COMPENSATING
                );

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.MANUAL_REVIEW);

        FeignException.NotFound notFound =
                mock(FeignException.NotFound.class);

        when(walletServiceClient.getWalletHoldStatus(subscriptionId))
                .thenThrow(notFound);

        when(holdingServiceClient.getHoldingSubscriptionStatus(
                SYSTEM_ROLE,
                subscriptionId
        )).thenReturn(
                ApiResponse.success(
                        holdingStatus,
                        "Holding 처리 상태 조회 성공"
                )
        );

        when(subscriptionCompensationTransactionService.compensate(
                subscriptionId,
                adminId,
                idempotencyKey,
                correlationId,
                null,
                holdingStatus
        )).thenReturn(response);

        // when
        SubscriptionCompensationResult result =
                service.compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId
                );

        // then
        assertThat(result.response()).isEqualTo(response);
        assertThat(result.replayed()).isFalse();

        verify(subscriptionCompensationTransactionService)
                .compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId,
                        null,
                        holdingStatus
                );
    }

    @Test
    @DisplayName("동일 멱등키로 완료된 보상 요청은 기존 결과를 반환한다")
    void replaysCompletedIdempotentRequest() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "completed-key";
        String requestHash = "request-hash";

        IdempotencyRequest existing =
                mock(IdempotencyRequest.class);

        Subscription subscription =
                mock(Subscription.class);

        when(subscriptionRequestHasher
                .hashCompensation(subscriptionId))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class),
                eq(adminId),
                eq(IdempotencyOperation
                        .COMPENSATE_SUBSCRIPTION.name()),
                eq(idempotencyKey),
                eq(requestHash),
                eq(RESOURCE_TYPE)
        )).thenReturn(0);

        when(idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        adminId,
                        IdempotencyOperation.COMPENSATE_SUBSCRIPTION,
                        idempotencyKey
                ))
                .thenReturn(Optional.of(existing));

        when(existing.getRequestHash())
                .thenReturn(requestHash);

        when(existing.getIdempotencyRequestStatus())
                .thenReturn(IdempotencyRequestStatus.COMPLETED);

        when(existing.getResourceId())
                .thenReturn(subscriptionId);

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionId())
                .thenReturn(subscriptionId);

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.COMPENSATING);

        // when
        SubscriptionCompensationResult result =
                service.compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        null
                );

        // then
        assertThat(result.replayed()).isTrue();
        assertThat(result.response().subscriptionId())
                .isEqualTo(subscriptionId);
        assertThat(result.response().subscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);

        verifyNoInteractions(walletServiceClient);
        verifyNoInteractions(holdingServiceClient);
        verifyNoInteractions(
                subscriptionCompensationTransactionService
        );
    }

    @Test
    @DisplayName("MANUAL_REVIEW 상태가 아니면 관리자 보상 요청을 거부한다")
    void rejectsSubscriptionThatIsNotManualReview() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "invalid-status-key";
        String requestHash = "request-hash";

        Subscription subscription = mock(Subscription.class);

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.PROCESSING);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        "correlation-id"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode
                                .SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
                );

        verify(subscriptionIdempotencyService)
                .fail(
                        adminId,
                        IdempotencyOperation
                                .COMPENSATE_SUBSCRIPTION.name(),
                        idempotencyKey,
                        SubscriptionErrorCode
                                .SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
                                .getStatus()
                                .value()
                );

        verifyNoInteractions(walletServiceClient);
        verifyNoInteractions(holdingServiceClient);
        verifyNoInteractions(
                subscriptionCompensationTransactionService
        );
    }

    @Test
    @DisplayName("Wallet 상태 조회 장애가 발생하면 보상 처리를 중단한다")
    void stopsCompensationWhenWalletServiceFails() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "wallet-failure-key";
        String requestHash = "request-hash";

        Subscription subscription = mock(Subscription.class);

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.MANUAL_REVIEW);

        when(walletServiceClient.getWalletHoldStatus(subscriptionId))
                .thenThrow(mock(FeignException.class));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        "correlation-id"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode
                                .WALLET_SERVICE_UNAVAILABLE
                );

        verify(subscriptionIdempotencyService)
                .fail(
                        adminId,
                        IdempotencyOperation
                                .COMPENSATE_SUBSCRIPTION.name(),
                        idempotencyKey,
                        SubscriptionErrorCode
                                .WALLET_SERVICE_UNAVAILABLE
                                .getStatus()
                                .value()
                );

        verifyNoInteractions(holdingServiceClient);
        verifyNoInteractions(
                subscriptionCompensationTransactionService
        );
    }

    @Test
    @DisplayName("Holding 상태 조회 장애가 발생하면 보상 처리를 중단한다")
    void stopsCompensationWhenHoldingServiceFails() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "holding-failure-key";
        String requestHash = "request-hash";

        Subscription subscription = mock(Subscription.class);

        WalletHoldStatusResponse walletStatus =
                walletStatus(subscriptionId);

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.MANUAL_REVIEW);

        when(walletServiceClient.getWalletHoldStatus(subscriptionId))
                .thenReturn(
                        ApiResponse.success(
                                walletStatus,
                                "Wallet Hold 조회 성공"
                        )
                );

        when(holdingServiceClient.getHoldingSubscriptionStatus(
                SYSTEM_ROLE,
                subscriptionId
        )).thenThrow(mock(FeignException.class));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        "correlation-id"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode
                                .HOLDING_SERVICE_UNAVAILABLE
                );

        verify(subscriptionIdempotencyService)
                .fail(
                        adminId,
                        IdempotencyOperation
                                .COMPENSATE_SUBSCRIPTION.name(),
                        idempotencyKey,
                        SubscriptionErrorCode
                                .HOLDING_SERVICE_UNAVAILABLE
                                .getStatus()
                                .value()
                );

        verifyNoInteractions(
                subscriptionCompensationTransactionService
        );
    }

    private void beginNewRequest(
            UUID subscriptionId,
            UUID adminId,
            String idempotencyKey,
            String requestHash
    ) {
        when(subscriptionRequestHasher
                .hashCompensation(subscriptionId))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class),
                eq(adminId),
                eq(IdempotencyOperation
                        .COMPENSATE_SUBSCRIPTION.name()),
                eq(idempotencyKey),
                eq(requestHash),
                eq(RESOURCE_TYPE)
        )).thenReturn(1);
    }

    private WalletHoldStatusResponse walletStatus(
            UUID subscriptionId
    ) {
        return new WalletHoldStatusResponse(
                subscriptionId,
                10_000L,
                WalletHoldStatus.HELD,
                Instant.now()
        );
    }

    private HoldingSubscriptionStatusResponse
    holdingStatusWithoutAllocation(
            UUID subscriptionId
    ) {
        return new HoldingSubscriptionStatusResponse(
                subscriptionId,
                null,
                null,
                null,
                0L,
                0L,
                false,
                false,
                false,
                null
        );
    }
}
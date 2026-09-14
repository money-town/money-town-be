package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCompensationResponse;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.CompensationStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.HoldingSubscriptionStatusResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.WalletHoldStatus;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.WalletHoldStatusResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionCompensationTransactionServiceTest {

    private static final String IDEMPOTENCY_KEY =
            "compensation-idempotency-key";

    private static final String CORRELATION_ID =
            "compensation-correlation-id";

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionCompensationRepository
            subscriptionCompensationRepository;

    @Mock
    private IdempotencyRequestRepository
            idempotencyRequestRepository;

    @Mock
    private SubscriptionEventPublisher
            subscriptionEventPublisher;

    @Mock
    private SubscriptionCompensationCompletionService
            subscriptionCompensationCompletionService;

    @InjectMocks
    private SubscriptionCompensationTransactionService service;

    @ParameterizedTest
    @EnumSource(
            value = WalletHoldStatus.class,
            names = {
                    "HELD",
                    "COMMITTED"
            }
    )
    @DisplayName("Wallet 또는 Holding 보상이 필요하면 보상 이벤트를 다시 발행한다")
    void republishesCompensationEventWhenExternalCompensationIsRequired(
            WalletHoldStatus walletHoldStatus
    ) {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId;
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();

        Subscription subscription =
                createManualReviewSubscription(
                        offeringId,
                        userId
                );

        subscriptionId = subscription.getSubscriptionId();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
        );

        SubscriptionCompensation compensation =
                spy(
                        SubscriptionCompensation.create(
                                subscriptionId
                        )
                );

        prepareLockedEntities(
                offeringId,
                subscriptionId,
                offering,
                subscription,
                compensation
        );

        when(offering.getAssetId())
                .thenReturn(assetId);

        WalletHoldStatusResponse walletStatus =
                new WalletHoldStatusResponse(
                        subscriptionId,
                        subscription.getAmount(),
                        walletHoldStatus,
                        Instant.now()
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                new HoldingSubscriptionStatusResponse(
                        subscriptionId,
                        holdingId,
                        assetId,
                        userId,
                        subscription.getQuantity(),
                        0L,
                        true,
                        false,
                        false,
                        Instant.now()
                );

        when(idempotencyRequestRepository.complete(
                adminId,
                IdempotencyOperation
                        .COMPENSATE_SUBSCRIPTION.name(),
                IDEMPOTENCY_KEY,
                subscriptionId,
                202
        )).thenReturn(1);

        // when
        SubscriptionCompensationResponse response =
                service.compensate(
                        subscriptionId,
                        adminId,
                        IDEMPOTENCY_KEY,
                        CORRELATION_ID,
                        walletStatus,
                        holdingStatus
                );

        // then
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);

        assertThat(response.subscriptionId())
                .isEqualTo(subscriptionId);

        assertThat(response.subscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);

        /*
         * HELD이면 RELEASE,
         * COMMITTED이면 REFUND가 필요하므로 Wallet 재시도 상태가 된다.
         */
        verify(compensation).prepareWalletRetry();

        /*
         * 지분은 배정됐지만 아직 회수되지 않았으므로
         * Holding 재시도 상태가 된다.
         */
        verify(compensation).prepareHoldingRetry();

        verify(subscriptionEventPublisher)
                .publishCompensationRequested(
                        subscription,
                        assetId,
                        CORRELATION_ID
                );

        verifyNoInteractions(
                subscriptionCompensationCompletionService
        );

        verifyIdempotencyCompleted(
                adminId,
                subscriptionId
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = WalletHoldStatus.class,
            names = {
                    "RELEASED",
                    "REFUNDED"
            }
    )
    @DisplayName("Wallet과 Holding 보상이 이미 완료됐다면 이벤트 없이 완료 처리를 호출한다")
    void completesWithoutPublishingWhenExternalCompensationIsFinished(
            WalletHoldStatus walletHoldStatus
    ) {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Subscription subscription =
                createManualReviewSubscription(
                        offeringId,
                        userId
                );

        UUID subscriptionId =
                subscription.getSubscriptionId();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
        );

        SubscriptionCompensation compensation =
                spy(
                        SubscriptionCompensation.create(
                                subscriptionId
                        )
                );

        prepareLockedEntities(
                offeringId,
                subscriptionId,
                offering,
                subscription,
                compensation
        );

        WalletHoldStatusResponse walletStatus =
                new WalletHoldStatusResponse(
                        subscriptionId,
                        subscription.getAmount(),
                        walletHoldStatus,
                        Instant.now()
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                new HoldingSubscriptionStatusResponse(
                        subscriptionId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        userId,
                        subscription.getQuantity(),
                        subscription.getQuantity(),
                        true,
                        true,
                        false,
                        Instant.now()
                );

        when(idempotencyRequestRepository.complete(
                adminId,
                IdempotencyOperation
                        .COMPENSATE_SUBSCRIPTION.name(),
                IDEMPOTENCY_KEY,
                subscriptionId,
                202
        )).thenReturn(1);

        // when
        SubscriptionCompensationResponse response =
                service.compensate(
                        subscriptionId,
                        adminId,
                        IDEMPOTENCY_KEY,
                        CORRELATION_ID,
                        walletStatus,
                        holdingStatus
                );

        // then
        assertThat(response.subscriptionId())
                .isEqualTo(subscriptionId);

        verify(compensation).markWalletSucceeded();
        verify(compensation).markHoldingSucceeded();

        assertThat(compensation.getWalletStatus())
                .isEqualTo(CompensationStatus.SUCCEEDED);

        assertThat(compensation.getHoldingStatus())
                .isEqualTo(CompensationStatus.SUCCEEDED);

        verify(subscriptionCompensationCompletionService)
                .completeIfReady(subscriptionId);

        verifyNoInteractions(subscriptionEventPublisher);

        verifyIdempotencyCompleted(
                adminId,
                subscriptionId
        );
    }

    @Test
    @DisplayName("Wallet Hold와 Holding 배정 이력이 없으면 추가 이벤트 없이 완료 처리한다")
    void completesWhenWalletHoldAndHoldingAllocationDoNotExist() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Subscription subscription =
                createManualReviewSubscription(
                        offeringId,
                        userId
                );

        UUID subscriptionId =
                subscription.getSubscriptionId();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
        );

        SubscriptionCompensation compensation =
                spy(
                        SubscriptionCompensation.create(
                                subscriptionId
                        )
                );

        prepareLockedEntities(
                offeringId,
                subscriptionId,
                offering,
                subscription,
                compensation
        );

        /*
         * walletStatus가 null이면 Wallet Service에서
         * 해당 청약의 Hold가 없다는 404 결과를 의미한다.
         */
        WalletHoldStatusResponse walletStatus = null;

        HoldingSubscriptionStatusResponse holdingStatus =
                new HoldingSubscriptionStatusResponse(
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

        when(idempotencyRequestRepository.complete(
                adminId,
                IdempotencyOperation
                        .COMPENSATE_SUBSCRIPTION.name(),
                IDEMPOTENCY_KEY,
                subscriptionId,
                202
        )).thenReturn(1);

        // when
        service.compensate(
                subscriptionId,
                adminId,
                IDEMPOTENCY_KEY,
                CORRELATION_ID,
                walletStatus,
                holdingStatus
        );

        // then
        verify(compensation).markWalletSucceeded();
        verify(compensation).markHoldingSucceeded();

        assertThat(compensation.getWalletStatus())
                .isEqualTo(CompensationStatus.SUCCEEDED);

        assertThat(compensation.getHoldingStatus())
                .isEqualTo(CompensationStatus.SUCCEEDED);

        verify(subscriptionCompensationCompletionService)
                .completeIfReady(subscriptionId);

        verifyNoInteractions(subscriptionEventPublisher);

        verifyIdempotencyCompleted(
                adminId,
                subscriptionId
        );
    }

    @Test
    @DisplayName("Wallet Hold 금액이 청약 금액과 다르면 외부 응답 오류로 처리한다")
    void rejectsMismatchedWalletAmount() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Subscription subscription =
                createManualReviewSubscription(
                        offeringId,
                        userId
                );

        UUID subscriptionId =
                subscription.getSubscriptionId();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
        );

        SubscriptionCompensation compensation =
                SubscriptionCompensation.create(
                        subscriptionId
                );

        prepareLockedEntities(
                offeringId,
                subscriptionId,
                offering,
                subscription,
                compensation
        );

        WalletHoldStatusResponse walletStatus =
                new WalletHoldStatusResponse(
                        subscriptionId,
                        subscription.getAmount() + 1L,
                        WalletHoldStatus.HELD,
                        Instant.now()
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                new HoldingSubscriptionStatusResponse(
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

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId,
                        adminId,
                        IDEMPOTENCY_KEY,
                        CORRELATION_ID,
                        walletStatus,
                        holdingStatus
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
                );

        /*
         * 실제 @Transactional 실행에서는 예외로 인해
         * restartCompensation과 보상 상태 변경도 롤백된다.
         */
        verifyNoInteractions(subscriptionEventPublisher);
        verifyNoInteractions(
                subscriptionCompensationCompletionService
        );

        verify(idempotencyRequestRepository, never())
                .complete(
                        adminId,
                        IdempotencyOperation
                                .COMPENSATE_SUBSCRIPTION.name(),
                        IDEMPOTENCY_KEY,
                        subscriptionId,
                        202
                );
    }

    @Test
    @DisplayName("Holding의 자산 정보가 공모와 다르면 외부 응답 오류로 처리한다")
    void rejectsMismatchedHoldingAsset() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        UUID offeringAssetId = UUID.randomUUID();
        UUID responseAssetId = UUID.randomUUID();

        Subscription subscription =
                createManualReviewSubscription(
                        offeringId,
                        userId
                );

        UUID subscriptionId =
                subscription.getSubscriptionId();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
        );

        SubscriptionCompensation compensation =
                SubscriptionCompensation.create(
                        subscriptionId
                );

        prepareLockedEntities(
                offeringId,
                subscriptionId,
                offering,
                subscription,
                compensation
        );

        when(offering.getAssetId())
                .thenReturn(offeringAssetId);

        WalletHoldStatusResponse walletStatus =
                new WalletHoldStatusResponse(
                        subscriptionId,
                        subscription.getAmount(),
                        WalletHoldStatus.RELEASED,
                        Instant.now()
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                new HoldingSubscriptionStatusResponse(
                        subscriptionId,
                        UUID.randomUUID(),
                        responseAssetId,
                        userId,
                        subscription.getQuantity(),
                        0L,
                        true,
                        false,
                        false,
                        Instant.now()
                );

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId,
                        adminId,
                        IDEMPOTENCY_KEY,
                        CORRELATION_ID,
                        walletStatus,
                        holdingStatus
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
                );

        verifyNoInteractions(subscriptionEventPublisher);
        verifyNoInteractions(
                subscriptionCompensationCompletionService
        );

        verify(idempotencyRequestRepository, never())
                .complete(
                        adminId,
                        IdempotencyOperation
                                .COMPENSATE_SUBSCRIPTION.name(),
                        IDEMPOTENCY_KEY,
                        subscriptionId,
                        202
                );
    }

    @Test
    @DisplayName("이미 취소 완료된 청약은 외부 보상 없이 기존 결과를 완료 처리한다")
    void completesExistingCancelledSubscription() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Subscription subscription =
                createCancelledSubscription(
                        offeringId,
                        userId
                );

        UUID subscriptionId =
                subscription.getSubscriptionId();

        when(subscriptionRepository
                .findByIdForUpdate(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(idempotencyRequestRepository.complete(
                adminId,
                IdempotencyOperation
                        .COMPENSATE_SUBSCRIPTION.name(),
                IDEMPOTENCY_KEY,
                subscriptionId,
                202
        )).thenReturn(1);

        // when
        SubscriptionCompensationResponse response =
                service.completeExistingResult(
                        subscriptionId,
                        adminId,
                        IDEMPOTENCY_KEY
                );

        // then
        assertThat(response.subscriptionId())
                .isEqualTo(subscriptionId);

        assertThat(response.subscriptionStatus())
                .isEqualTo(SubscriptionStatus.CANCELLED);

        verifyIdempotencyCompleted(
                adminId,
                subscriptionId
        );

        verifyNoInteractions(offeringRepository);
        verifyNoInteractions(
                subscriptionCompensationRepository
        );
        verifyNoInteractions(subscriptionEventPublisher);
        verifyNoInteractions(
                subscriptionCompensationCompletionService
        );
    }

    @Test
    @DisplayName("멱등 요청 완료 갱신에 실패하면 예외를 발생시킨다")
    void rejectsWhenIdempotencyCompletionFails() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Subscription subscription =
                createManualReviewSubscription(
                        offeringId,
                        userId
                );

        UUID subscriptionId =
                subscription.getSubscriptionId();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
        );

        SubscriptionCompensation compensation =
                SubscriptionCompensation.create(
                        subscriptionId
                );

        prepareLockedEntities(
                offeringId,
                subscriptionId,
                offering,
                subscription,
                compensation
        );

        HoldingSubscriptionStatusResponse holdingStatus =
                new HoldingSubscriptionStatusResponse(
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

        when(idempotencyRequestRepository.complete(
                adminId,
                IdempotencyOperation
                        .COMPENSATE_SUBSCRIPTION.name(),
                IDEMPOTENCY_KEY,
                subscriptionId,
                202
        )).thenReturn(0);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId,
                        adminId,
                        IDEMPOTENCY_KEY,
                        CORRELATION_ID,
                        null,
                        holdingStatus
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode
                                .IDEMPOTENCY_COMPLETION_FAILED
                );
    }

    /**
     * 공모 취소 보상이 진행되다가 운영 확인이 필요해진 청약을 만든다.
     */
    private Subscription createManualReviewSubscription(
            UUID offeringId,
            UUID userId
    ) {
        Subscription subscription =
                Subscription.create(
                        offeringId,
                        userId,
                        10L,
                        1_000L,
                        Instant.now().plusSeconds(600)
                );

        subscription.startCompensation(
                CancellationType.OFFERING_ADMIN_CANCELLED
        );

        subscription.requireManualReview(
                "COMPENSATION_RETRY_REQUIRED"
        );

        return subscription;
    }

    /**
     * 보상과 수량 복원이 끝나 최종 취소된 청약을 만든다.
     */
    private Subscription createCancelledSubscription(
            UUID offeringId,
            UUID userId
    ) {
        Subscription subscription =
                Subscription.create(
                        offeringId,
                        userId,
                        10L,
                        1_000L,
                        Instant.now().plusSeconds(600)
                );

        subscription.startCompensation(
                CancellationType.OFFERING_ADMIN_CANCELLED
        );

        subscription.markCompensationQuantityRestored();
        subscription.completeCancellation(Instant.now());

        return subscription;
    }

    /**
     * Transaction Service가 사용하는 잠금 조회 결과를 준비한다.
     */
    private void prepareLockedEntities(
            UUID offeringId,
            UUID subscriptionId,
            Offering offering,
            Subscription subscription,
            SubscriptionCompensation compensation
    ) {
        when(subscriptionRepository
                .findOfferingIdBySubscriptionId(subscriptionId))
                .thenReturn(Optional.of(offeringId));

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        when(subscriptionRepository.findByIdForUpdate(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscriptionCompensationRepository
                .findBySubscriptionIdForUpdate(subscriptionId))
                .thenReturn(Optional.of(compensation));
    }

    private void verifyIdempotencyCompleted(
            UUID adminId,
            UUID subscriptionId
    ) {
        verify(idempotencyRequestRepository)
                .complete(
                        adminId,
                        IdempotencyOperation
                                .COMPENSATE_SUBSCRIPTION.name(),
                        IDEMPOTENCY_KEY,
                        subscriptionId,
                        202
                );
    }
}
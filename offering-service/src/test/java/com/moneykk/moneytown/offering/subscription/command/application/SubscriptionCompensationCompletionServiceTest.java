package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCompensationCompletionService;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.CompensationStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionCompensationCompletionServiceTest {

    private final UUID offeringId = UUID.randomUUID();
    private Subscription subscription;
    private SubscriptionCompensation compensation;
    private Offering offering;

    @Mock private OfferingRepository offeringRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionCompensationRepository subscriptionCompensationRepository;
    @Mock private OfferingCompensationCompletionService offeringCompensationCompletionService;
    @Mock private EntityManager entityManager;
    @InjectMocks private SubscriptionCompensationCompletionService service;

    @BeforeEach
    void setUp() {
        subscription = Subscription.create(
                offeringId, UUID.randomUUID(), 10L, 1_000L,
                Instant.now().plusSeconds(600)
        );
        subscription.startCompensation(CancellationType.OFFERING_UNDER_SUBSCRIBED);
        compensation = SubscriptionCompensation.create(subscription.getSubscriptionId());
        offering = Offering.create(
                UUID.randomUUID(), UUID.randomUUID(), "테스트 공모",
                1_000L, 100L, 1L, 100L,
                Instant.now().minusSeconds(3_600), Instant.now().minusSeconds(60)
        );
        ReflectionTestUtils.setField(offering, "offeringId", offeringId);
        ReflectionTestUtils.setField(offering, "offeringStatus", OfferingStatus.OPEN);
        ReflectionTestUtils.setField(offering, "remainingQuantity", 90L);
        offering.startUnderSubscribedCancellation();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Wallet과 Holding 중 어느 상태가 먼저 성공해도 양쪽 성공 후 한 번만 복원한다")
    void completesAfterBothResultsRegardlessOfOrder(boolean walletFirst) {
        stubSubscription();
        stubCompensation();
        when(offeringRepository.restoreQuantity(
                offeringId, 10L, JpaAuditingConfig.SYSTEM_USER_ID
        )).thenReturn(1);

        if (walletFirst) {
            compensation.markWalletSucceeded();
        } else {
            compensation.markHoldingSucceeded();
        }

        assertThat(service.completeIfReady(subscription.getSubscriptionId())).isFalse();
        assertThat(subscription.isQuantityReserved()).isTrue();
        verify(offeringRepository, never()).restoreQuantity(
                offeringId, 10L, JpaAuditingConfig.SYSTEM_USER_ID
        );

        if (walletFirst) {
            compensation.markHoldingSucceeded();
        } else {
            compensation.markWalletSucceeded();
        }

        // 후속 공모 완료 판정 전에 청약 상태가 변경됐는지도 검증한다.
        when(offeringCompensationCompletionService.completeIfReady(offeringId))
                .thenAnswer(invocation -> {
                    assertThat(subscription.getSubscriptionStatus())
                            .isEqualTo(SubscriptionStatus.CANCELLED);
                    assertThat(subscription.isQuantityReserved()).isFalse();
                    return false;
                });

        assertThat(service.completeIfReady(subscription.getSubscriptionId())).isTrue();
        Instant cancelledAt = subscription.getCancelledAt();
        assertThat(cancelledAt).isNotNull();
        assertThat(subscription.getCancellationType())
                .isEqualTo(CancellationType.OFFERING_UNDER_SUBSCRIBED);

        assertThat(service.completeIfReady(subscription.getSubscriptionId())).isFalse();
        assertThat(subscription.getCancelledAt()).isEqualTo(cancelledAt);
        verify(offeringRepository, times(1)).restoreQuantity(
                offeringId, 10L, JpaAuditingConfig.SYSTEM_USER_ID
        );
        verify(entityManager).refresh(offering);
        verify(offeringCompensationCompletionService).completeIfReady(offeringId);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING,PENDING", "PENDING,SUCCEEDED", "PENDING,FAILED",
            "SUCCEEDED,PENDING", "SUCCEEDED,FAILED",
            "FAILED,PENDING", "FAILED,SUCCEEDED", "FAILED,FAILED"
    })
    @DisplayName("한쪽이라도 미완료 또는 실패이면 수량 복원과 취소를 진행하지 않는다")
    void waitsForBothSuccesses(CompensationStatus wallet, CompensationStatus holding) {
        setCompensationStatuses(wallet, holding);
        stubSubscription();
        stubCompensation();

        assertThat(service.completeIfReady(subscription.getSubscriptionId())).isFalse();
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);
        assertThat(subscription.isQuantityReserved()).isTrue();
        assertThat(subscription.getCancelledAt()).isNull();
        verify(offeringRepository, never()).restoreQuantity(
                offeringId, 10L, JpaAuditingConfig.SYSTEM_USER_ID
        );
        verifyNoInteractions(entityManager, offeringCompensationCompletionService);
    }

    @Test
    @DisplayName("수량이 이미 복원됐으면 다시 복원하지 않고 청약 취소만 완료한다")
    void skipsAlreadyRestoredQuantity() {
        succeedBoth();
        subscription.markCompensationQuantityRestored();
        ReflectionTestUtils.setField(offering, "remainingQuantity", 100L);
        stubSubscription();
        stubCompensation();

        assertThat(service.completeIfReady(subscription.getSubscriptionId())).isTrue();
        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        verify(offeringRepository, never()).restoreQuantity(
                offeringId, 10L, JpaAuditingConfig.SYSTEM_USER_ID
        );
        verifyNoInteractions(entityManager);
        verify(offeringCompensationCompletionService).completeIfReady(offeringId);
    }

    @Test
    @DisplayName("양쪽 보상이 성공해도 MANUAL_REVIEW 청약은 자동 취소하지 않는다")
    void preservesManualReview() {
        succeedBoth();
        subscription.requireManualReview("REVIEW_REQUIRED");
        stubSubscription();

        assertThat(service.completeIfReady(subscription.getSubscriptionId())).isFalse();
        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.MANUAL_REVIEW);
        assertThat(subscription.isQuantityReserved()).isTrue();
        verifyNoInteractions(
                subscriptionCompensationRepository, entityManager,
                offeringCompensationCompletionService
        );
    }

    @Test
    @DisplayName("공모 취소 사유가 없는 타임아웃 청약은 완료 대상에서 제외한다")
    void excludesTimeout() {
        subscription = Subscription.create(
                offeringId, UUID.randomUUID(), 10L, 1_000L,
                Instant.now().plusSeconds(600)
        );
        subscription.startExpirationCompensation(subscription.getReservationExpiresAt());
        stubSubscription();

        assertThat(service.completeIfReady(subscription.getSubscriptionId())).isFalse();
        assertThat(subscription.getFailureCode()).isEqualTo("RESERVATION_EXPIRED");
        assertThat(subscription.isQuantityReserved()).isTrue();
        verifyNoInteractions(
                subscriptionCompensationRepository, entityManager,
                offeringCompensationCompletionService
        );
    }

    @Test
    @DisplayName("실제 수량 복원이 실패하면 청약 취소를 진행하지 않고 예외를 전달한다")
    void propagatesQuantityRestoreFailure() {
        succeedBoth();
        stubSubscription();
        stubCompensation();
        when(offeringRepository.restoreQuantity(
                offeringId, 10L, JpaAuditingConfig.SYSTEM_USER_ID
        )).thenReturn(0);

        assertThatThrownBy(() -> service.completeIfReady(subscription.getSubscriptionId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("수량 복원에 실패");

        assertThat(subscription.isQuantityReserved()).isTrue();
        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.COMPENSATING);
        assertThat(subscription.getCancelledAt()).isNull();
        verifyNoInteractions(entityManager, offeringCompensationCompletionService);
    }

    @Test
    @DisplayName("공모가 CANCELLING이 아니면 보상 완료를 거부한다")
    void rejectsOfferingOutsideCancellation() {
        succeedBoth();
        ReflectionTestUtils.setField(offering, "offeringStatus", OfferingStatus.OPEN);
        stubSubscription();
        stubCompensation();

        assertThatThrownBy(() -> service.completeIfReady(subscription.getSubscriptionId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("취소 진행 중인 공모");
        assertThat(subscription.isQuantityReserved()).isTrue();
        verifyNoInteractions(entityManager, offeringCompensationCompletionService);
    }

    @Test
    @DisplayName("보상 진행 정보가 없으면 완료를 거부한다")
    void rejectsMissingCompensation() {
        stubSubscription();
        when(subscriptionCompensationRepository.findBySubscriptionIdForUpdate(
                subscription.getSubscriptionId()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeIfReady(subscription.getSubscriptionId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("보상 진행 정보가 없습니다");
        assertThat(subscription.isQuantityReserved()).isTrue();
        verifyNoInteractions(entityManager, offeringCompensationCompletionService);
    }

    @Test
    @DisplayName("공모 완료 처리의 예외를 삼키지 않는다")
    void propagatesOfferingCompletionFailure() {
        succeedBoth();
        subscription.markCompensationQuantityRestored();
        stubSubscription();
        stubCompensation();
        IllegalStateException failure = new IllegalStateException("공모 완료 실패");
        when(offeringCompensationCompletionService.completeIfReady(offeringId))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.completeIfReady(subscription.getSubscriptionId()))
                .isSameAs(failure);
        // 실제 DB 롤백은 Mockito 단위 테스트가 아닌 트랜잭션 통합 테스트 대상이다.
    }

    private void stubSubscription() {
        when(subscriptionRepository.findOfferingIdBySubscriptionId(
                subscription.getSubscriptionId()
        )).thenReturn(Optional.of(offeringId));
        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));
        when(subscriptionRepository.findByIdForUpdate(subscription.getSubscriptionId()))
                .thenReturn(Optional.of(subscription));
    }

    private void stubCompensation() {
        when(subscriptionCompensationRepository.findBySubscriptionIdForUpdate(
                subscription.getSubscriptionId()
        )).thenReturn(Optional.of(compensation));
    }

    private void succeedBoth() {
        compensation.markWalletSucceeded();
        compensation.markHoldingSucceeded();
    }

    private void setCompensationStatuses(CompensationStatus wallet, CompensationStatus holding) {
        if (wallet == CompensationStatus.SUCCEEDED) compensation.markWalletSucceeded();
        if (wallet == CompensationStatus.FAILED) compensation.markWalletFailed("REFUND_FAILED");
        if (holding == CompensationStatus.SUCCEEDED) compensation.markHoldingSucceeded();
        if (holding == CompensationStatus.FAILED) compensation.markHoldingFailed("HOLDING_REVOCATION_FAILED");
    }
}


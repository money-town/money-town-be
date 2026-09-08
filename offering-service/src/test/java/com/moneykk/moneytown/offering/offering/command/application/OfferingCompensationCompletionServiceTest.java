package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingCompensationCompletionServiceTest {

    private final UUID offeringId = UUID.randomUUID();

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private OfferingCompensationCompletionService
            offeringCompensationCompletionService;

    @Test
    @DisplayName("미해결 청약이 없고 수량이 모두 복원되면 공모 취소를 완료한다")
    void completesCancellation() {
        Offering offering = newCancellingOffering(100L);
        stubOffering(offering);

        when(subscriptionRepository.existsUnresolvedByOfferingId(offeringId))
                .thenReturn(false);

        Instant before = Instant.now();

        boolean result =
                offeringCompensationCompletionService.completeIfReady(
                        offeringId
                );

        Instant after = Instant.now();

        assertThat(result).isTrue();
        assertThat(offering.getOfferingStatus())
                .isEqualTo(OfferingStatus.CANCELLED);
        assertThat(offering.getCancellationType())
                .isEqualTo(CancellationType.UNDER_SUBSCRIBED);
        assertThat(offering.getCancelledAt())
                .isNotNull()
                .isBetween(before, after);
        assertThat(offering.getRemainingQuantity()).isEqualTo(100L);

        verify(subscriptionRepository)
                .existsUnresolvedByOfferingId(offeringId);
    }

    @Test
    @DisplayName("수량이 모두 복원됐어도 미해결 청약이 있으면 취소를 완료하지 않는다")
    void keepsCancellingWhenUnresolvedSubscriptionExists() {
        Offering offering = newCancellingOffering(100L);
        stubOffering(offering);

        when(subscriptionRepository.existsUnresolvedByOfferingId(offeringId))
                .thenReturn(true);

        boolean result =
                offeringCompensationCompletionService.completeIfReady(
                        offeringId
                );

        assertThat(result).isFalse();
        assertThat(offering.getOfferingStatus())
                .isEqualTo(OfferingStatus.CANCELLING);
        assertThat(offering.getCancelledAt()).isNull();
        assertThat(offering.getCancellationType())
                .isEqualTo(CancellationType.UNDER_SUBSCRIBED);
        assertThat(offering.getRemainingQuantity()).isEqualTo(100L);
    }

    @Test
    @DisplayName("이미 취소된 공모는 취소 시각을 변경하거나 청약을 다시 조회하지 않는다")
    void preservesAlreadyCancelledOffering() {
        Offering offering = newCancellingOffering(100L);
        Instant originalCancelledAt = Instant.now().minusSeconds(60);
        offering.completeCancellation(originalCancelledAt);

        stubOffering(offering);

        boolean result =
                offeringCompensationCompletionService.completeIfReady(
                        offeringId
                );

        assertThat(result).isFalse();
        assertThat(offering.getOfferingStatus())
                .isEqualTo(OfferingStatus.CANCELLED);
        assertThat(offering.getCancelledAt())
                .isEqualTo(originalCancelledAt);
        assertThat(offering.getRemainingQuantity()).isEqualTo(100L);

        verifyNoInteractions(subscriptionRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = OfferingStatus.class,
            names = {"CANCELLING", "CANCELLED"},
            mode = EnumSource.Mode.EXCLUDE
    )
    @DisplayName("취소 진행 상태가 아닌 공모는 변경하지 않는다")
    void ignoresOfferingNotCancelling(OfferingStatus status) {
        Offering offering = newOffering();

        ReflectionTestUtils.setField(
                offering,
                "offeringStatus",
                status
        );

        stubOffering(offering);

        boolean result =
                offeringCompensationCompletionService.completeIfReady(
                        offeringId
                );

        assertThat(result).isFalse();
        assertThat(offering.getOfferingStatus()).isEqualTo(status);
        assertThat(offering.getCancelledAt()).isNull();
        assertThat(offering.getRemainingQuantity()).isEqualTo(100L);

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("미해결 청약이 없어도 확보 수량이 덜 복원됐으면 취소 완료를 거부한다")
    void rejectsCompletionWhenQuantityNotFullyRestored() {
        Offering offering = newCancellingOffering(90L);
        stubOffering(offering);

        when(subscriptionRepository.existsUnresolvedByOfferingId(offeringId))
                .thenReturn(false);

        assertThatThrownBy(() ->
                offeringCompensationCompletionService.completeIfReady(
                        offeringId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception).getErrorCode()
                        ).isEqualTo(
                                OfferingErrorCode.OFFERING_QUANTITY_STATE_INVALID
                        )
                );

        assertThat(offering.getOfferingStatus())
                .isEqualTo(OfferingStatus.CANCELLING);
        assertThat(offering.getCancelledAt()).isNull();
        assertThat(offering.getRemainingQuantity()).isEqualTo(90L);
    }

    @Test
    @DisplayName("취소 사유가 없는 공모는 취소 완료를 거부한다")
    void rejectsCompletionWithoutCancellationType() {
        Offering offering = newCancellingOffering(100L);

        ReflectionTestUtils.setField(
                offering,
                "cancellationType",
                null
        );

        stubOffering(offering);

        when(subscriptionRepository.existsUnresolvedByOfferingId(offeringId))
                .thenReturn(false);

        assertThatThrownBy(() ->
                offeringCompensationCompletionService.completeIfReady(
                        offeringId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception).getErrorCode()
                        ).isEqualTo(
                                OfferingErrorCode
                                        .OFFERING_CANCELLATION_COMPLETION_NOT_ALLOWED
                        )
                );

        assertThat(offering.getOfferingStatus())
                .isEqualTo(OfferingStatus.CANCELLING);
        assertThat(offering.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("공모가 없으면 조회 실패를 반환한다")
    void rejectsMissingOffering() {
        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                offeringCompensationCompletionService.completeIfReady(
                        offeringId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception).getErrorCode()
                        ).isEqualTo(OfferingErrorCode.OFFERING_NOT_FOUND)
                );

        verifyNoInteractions(subscriptionRepository);
    }

    private Offering newOffering() {
        Instant now = Instant.now();

        Offering offering = Offering.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "테스트 공모",
                1_000L,
                100L,
                1L,
                100L,
                now.minusSeconds(3_600),
                now.minusSeconds(60)
        );

        // DB 저장 없이 생성된 엔티티에 테스트 식별자를 설정한다.
        ReflectionTestUtils.setField(
                offering,
                "offeringId",
                offeringId
        );

        return offering;
    }

    private Offering newCancellingOffering(long remainingQuantity) {
        Offering offering = newOffering();

        ReflectionTestUtils.setField(
                offering,
                "offeringStatus",
                OfferingStatus.OPEN
        );
        ReflectionTestUtils.setField(
                offering,
                "remainingQuantity",
                remainingQuantity
        );

        offering.startUnderSubscribedCancellation();

        return offering;
    }

    private void stubOffering(Offering offering) {
        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));
    }
}
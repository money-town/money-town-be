package com.moneykk.moneytown.offering.offering.domain.entity;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OfferingTest {

    @Test
    @DisplayName("모집 종료 후 잔여 수량이 남은 OPEN 공모는 CANCELLING으로 전환된다")
    void startsUnderSubscribedCancellation() {
        // given
        Offering offering = createExpiredOpenOffering(50L);

        // when
        offering.startUnderSubscribedCancellation();

        // then
        assertThat(offering.getOfferingStatus())
                .isEqualTo(OfferingStatus.CANCELLING);

        assertThat(offering.getCancellationType())
                .isEqualTo(CancellationType.UNDER_SUBSCRIBED);

        assertThat(offering.getCancelledAt())
                .isNull();
    }

    @Test
    @DisplayName("OPEN 상태가 아니면 모집 미달 취소 처리를 시작할 수 없다")
    void cannotStartCancellationWhenNotOpen() {
        // given
        Offering offering = createExpiredOpenOffering(50L);

        ReflectionTestUtils.setField(
                offering,
                "offeringStatus",
                OfferingStatus.SCHEDULED
        );

        // when & then
        assertThatThrownBy(
                offering::startUnderSubscribedCancellation
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception).getErrorCode()
                        ).isEqualTo(
                                OfferingErrorCode.OFFERING_CANCELLATION_NOT_ALLOWED
                        )
                );
    }

    @Test
    @DisplayName("모집 종료 시간이 도래하지 않으면 모집 미달 취소 처리를 시작할 수 없다")
    void cannotStartCancellationBeforeEndAt() {
        // given
        Offering offering = createOpenOfferingBeforeEndAt();

        // when & then
        assertThatThrownBy(
                offering::startUnderSubscribedCancellation
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception).getErrorCode()
                        ).isEqualTo(
                                OfferingErrorCode.OFFERING_CANCELLATION_NOT_ALLOWED
                        )
                );
    }

    @Test
    @DisplayName("잔여 수량이 없으면 모집 미달 취소 처리 대상이 아니다")
    void cannotStartCancellationWhenNoRemainingQuantity() {
        // given
        Offering offering = createExpiredOpenOffering(0L);

        // when & then
        assertThatThrownBy(
                offering::startUnderSubscribedCancellation
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception).getErrorCode()
                        ).isEqualTo(
                                OfferingErrorCode.OFFERING_QUANTITY_STATE_INVALID
                        )
                );
    }

    private Offering createExpiredOpenOffering(Long remainingQuantity) {
        Instant now = Instant.now();

        Offering offering = Offering.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "테스트 공모",
                10_000L,
                100L,
                1L,
                100L,
                now.minusSeconds(3600),
                now.minusSeconds(60)
        );

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

        return offering;
    }

    private Offering createOpenOfferingBeforeEndAt() {
        Instant now = Instant.now();

        Offering offering = Offering.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "테스트 공모",
                10_000L,
                100L,
                1L,
                100L,
                now.minusSeconds(3600),
                now.plusSeconds(3600)
        );

        ReflectionTestUtils.setField(
                offering,
                "offeringStatus",
                OfferingStatus.OPEN
        );

        return offering;
    }

    @ParameterizedTest
    @EnumSource(
            value = OfferingStatus.class,
            names = {
                    "SCHEDULED",
                    "OPEN",
                    "SOLD_OUT",
                    "CLOSED"
            }
    )
    @DisplayName("관리자는 승인 이후의 공모 상태에서 중단 처리를 시작할 수 있다")
    void startsAdminCancellationFromAllowedStatus(
            OfferingStatus currentStatus
    ) {
        // given
        Offering offering =
                createOfferingForAdminCancellation();

        ReflectionTestUtils.setField(
                offering,
                "offeringStatus",
                currentStatus
        );

        // when
        offering.startAdminCancellation();

        // then
        assertThat(offering.getOfferingStatus())
                .isEqualTo(OfferingStatus.CANCELLING);

        assertThat(offering.getCancellationType())
                .isEqualTo(CancellationType.ADMIN_CANCELLED);

        /*
         * 실제 취소 시각은 보상 완료 후
         * completeCancellation()에서 기록한다.
         */
        assertThat(offering.getCancelledAt())
                .isNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = OfferingStatus.class,
            names = {
                    "DRAFT",
                    "REVIEW_REQUESTED",
                    "REJECTED",
                    "CANCELLING",
                    "CANCELLED"
            }
    )
    @DisplayName("관리자 중단이 허용되지 않는 공모 상태에서는 예외가 발생한다")
    void cannotStartAdminCancellationFromDisallowedStatus(
            OfferingStatus currentStatus
    ) {
        // given
        Offering offering =
                createOfferingForAdminCancellation();

        ReflectionTestUtils.setField(
                offering,
                "offeringStatus",
                currentStatus
        );

        // when & then
        assertThatThrownBy(
                offering::startAdminCancellation
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                OfferingErrorCode
                                        .OFFERING_CANCELLATION_NOT_ALLOWED
                        )
                );

        assertThat(offering.getOfferingStatus())
                .isEqualTo(currentStatus);

        assertThat(offering.getCancellationType())
                .isNull();

        assertThat(offering.getCancelledAt())
                .isNull();
    }

    private Offering createOfferingForAdminCancellation() {
        Instant now = Instant.now();

        return Offering.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "관리자 중단 테스트 공모",
                10_000L,
                100L,
                1L,
                100L,
                now.minusSeconds(3600),
                now.plusSeconds(3600)
        );
    }

    private Offering createDraftOffering() {
        Instant now = Instant.now();

        return Offering.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "테스트 공모",
                10_000L,
                100L,
                1L,
                100L,
                now.plusSeconds(3600),
                now.plusSeconds(7200)
        );
    }

    @Test
    @DisplayName("유효한 값으로 공모를 생성하면 DRAFT 상태이며 잔여 수량이 총 수량과 같다")
    void createsOfferingInDraftStatus() {
        // given
        UUID assetId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        Instant now = Instant.now();

        // when
        Offering offering = Offering.create(
                assetId,
                issuerId,
                "테스트 공모",
                10_000L,
                100L,
                1L,
                100L,
                now.plusSeconds(3600),
                now.plusSeconds(7200)
        );

        // then
        assertThat(offering.getAssetId()).isEqualTo(assetId);
        assertThat(offering.getIssuerId()).isEqualTo(issuerId);
        assertThat(offering.getOfferingStatus())
                .isEqualTo(OfferingStatus.DRAFT);
        assertThat(offering.getTotalQuantity()).isEqualTo(100L);
        assertThat(offering.getRemainingQuantity()).isEqualTo(100L);
    }

    @Test
    @DisplayName("자산 ID가 없으면 공모를 생성할 수 없다")
    void rejectsCreateWithoutAssetId() {
        Instant now = Instant.now();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> Offering.create(
                        null, UUID.randomUUID(), "테스트 공모", 10_000L,
                        100L, 1L, 100L,
                        now.plusSeconds(3600), now.plusSeconds(7200)
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_OFFERING_INPUT);
    }

    @Test
    @DisplayName("공모주 ID가 없으면 공모를 생성할 수 없다")
    void rejectsCreateWithoutIssuerId() {
        Instant now = Instant.now();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> Offering.create(
                        UUID.randomUUID(), null, "테스트 공모", 10_000L,
                        100L, 1L, 100L,
                        now.plusSeconds(3600), now.plusSeconds(7200)
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_OFFERING_INPUT);
    }

    @Test
    @DisplayName("공모 상품명이 비어 있으면 공모를 생성할 수 없다")
    void rejectsCreateWithBlankTitle() {
        Instant now = Instant.now();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> Offering.create(
                        UUID.randomUUID(), UUID.randomUUID(), "  ", 10_000L,
                        100L, 1L, 100L,
                        now.plusSeconds(3600), now.plusSeconds(7200)
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_OFFERING_TITLE);
    }

    @Test
    @DisplayName("공모 상품명이 200자를 초과하면 공모를 생성할 수 없다")
    void rejectsCreateWithTooLongTitle() {
        Instant now = Instant.now();
        String longTitle = "가".repeat(201);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> Offering.create(
                        UUID.randomUUID(), UUID.randomUUID(), longTitle, 10_000L,
                        100L, 1L, 100L,
                        now.plusSeconds(3600), now.plusSeconds(7200)
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_OFFERING_TITLE);
    }

    @Test
    @DisplayName("조각당 단위 가격이 0 이하이면 공모를 생성할 수 없다")
    void rejectsCreateWithNonPositivePrice() {
        Instant now = Instant.now();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> Offering.create(
                        UUID.randomUUID(), UUID.randomUUID(), "테스트 공모", 0L,
                        100L, 1L, 100L,
                        now.plusSeconds(3600), now.plusSeconds(7200)
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_OFFERING_PRICE);
    }

    @Test
    @DisplayName("총 모집 수량이 0 이하이면 공모를 생성할 수 없다")
    void rejectsCreateWithNonPositiveTotalQuantity() {
        Instant now = Instant.now();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> Offering.create(
                        UUID.randomUUID(), UUID.randomUUID(), "테스트 공모", 10_000L,
                        0L, 1L, 100L,
                        now.plusSeconds(3600), now.plusSeconds(7200)
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_OFFERING_QUANTITY);
    }

    @Test
    @DisplayName("최소 청약 수량이 1보다 작으면 공모를 생성할 수 없다")
    void rejectsCreateWithInvalidMinSubscriptionQuantity() {
        Instant now = Instant.now();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> Offering.create(
                        UUID.randomUUID(), UUID.randomUUID(), "테스트 공모", 10_000L,
                        100L, 0L, 100L,
                        now.plusSeconds(3600), now.plusSeconds(7200)
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_SUBSCRIPTION_QUANTITY_RANGE);
    }

    @Test
    @DisplayName("최대 청약 수량이 최소 청약 수량보다 작으면 공모를 생성할 수 없다")
    void rejectsCreateWhenMaxIsLessThanMin() {
        Instant now = Instant.now();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> Offering.create(
                        UUID.randomUUID(), UUID.randomUUID(), "테스트 공모", 10_000L,
                        100L, 10L, 5L,
                        now.plusSeconds(3600), now.plusSeconds(7200)
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_SUBSCRIPTION_QUANTITY_RANGE);
    }

    @Test
    @DisplayName("최대 청약 수량이 총 모집 수량을 초과하면 공모를 생성할 수 없다")
    void rejectsCreateWhenMaxExceedsTotalQuantity() {
        Instant now = Instant.now();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> Offering.create(
                        UUID.randomUUID(), UUID.randomUUID(), "테스트 공모", 10_000L,
                        100L, 1L, 101L,
                        now.plusSeconds(3600), now.plusSeconds(7200)
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_SUBSCRIPTION_QUANTITY_RANGE);
    }

    @Test
    @DisplayName("모집 시작 시각이 모집 종료 시각보다 이후이면 공모를 생성할 수 없다")
    void rejectsCreateWhenStartIsNotBeforeEnd() {
        Instant now = Instant.now();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> Offering.create(
                        UUID.randomUUID(), UUID.randomUUID(), "테스트 공모", 10_000L,
                        100L, 1L, 100L,
                        now.plusSeconds(7200), now.plusSeconds(3600)
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_OFFERING_PERIOD);
    }

    @Test
    @DisplayName("DRAFT 상태의 공모는 모집 시작 전이면 심사 요청 상태로 전환된다")
    void requestsReviewFromDraft() {
        // given
        Offering offering = createDraftOffering();

        // when
        offering.requestReview();

        // then
        assertThat(offering.getOfferingStatus())
                .isEqualTo(OfferingStatus.REVIEW_REQUESTED);
        assertThat(offering.getReviewRequestedAt()).isNotNull();
    }

    @Test
    @DisplayName("DRAFT 상태가 아니면 심사 요청을 할 수 없다")
    void rejectsRequestReviewWhenNotDraft() {
        // given
        Offering offering = createDraftOffering();
        ReflectionTestUtils.setField(
                offering, "offeringStatus", OfferingStatus.REVIEW_REQUESTED
        );

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class, offering::requestReview
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_REVIEW_REQUEST_NOT_ALLOWED);
    }

    @Test
    @DisplayName("모집 시작 시각이 지났으면 심사 요청을 할 수 없다")
    void rejectsRequestReviewAfterOfferingStarts() {
        // given
        Offering offering = createDraftOffering();
        ReflectionTestUtils.setField(
                offering, "startAt", Instant.now().minusSeconds(1)
        );

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class, offering::requestReview
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_PERIOD_EXPIRED);
    }

    private Offering createReviewRequestedOffering() {
        Offering offering = createDraftOffering();
        ReflectionTestUtils.setField(
                offering, "offeringStatus", OfferingStatus.REVIEW_REQUESTED
        );
        return offering;
    }

    @Test
    @DisplayName("심사 요청된 공모를 승인하면 SCHEDULED 상태로 전환된다")
    void approvesReviewRequestedOffering() {
        // given
        Offering offering = createReviewRequestedOffering();
        UUID reviewerId = UUID.randomUUID();

        // when
        offering.approve(reviewerId);

        // then
        assertThat(offering.getOfferingStatus())
                .isEqualTo(OfferingStatus.SCHEDULED);
        assertThat(offering.getReviewedAt()).isNotNull();
        assertThat(offering.getReviewedBy()).isEqualTo(reviewerId);
        assertThat(offering.getRejectionReason()).isNull();
    }

    @Test
    @DisplayName("REVIEW_REQUESTED 상태가 아니면 승인할 수 없다")
    void rejectsApprovalWhenNotReviewRequested() {
        // given
        Offering offering = createDraftOffering();

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.approve(UUID.randomUUID())
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_APPROVAL_NOT_ALLOWED);
    }

    @Test
    @DisplayName("승인자 ID가 없으면 승인할 수 없다")
    void rejectsApprovalWithoutReviewerId() {
        // given
        Offering offering = createReviewRequestedOffering();

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.approve(null)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_OFFERING_INPUT);
    }

    @Test
    @DisplayName("잔여 수량이 총 수량과 다르면 승인할 수 없다")
    void rejectsApprovalWhenQuantityStateInvalid() {
        // given
        Offering offering = createReviewRequestedOffering();
        ReflectionTestUtils.setField(
                offering, "remainingQuantity", 50L
        );

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.approve(UUID.randomUUID())
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_QUANTITY_STATE_INVALID);
    }

    @Test
    @DisplayName("모집 시작 시각이 지났으면 승인할 수 없다")
    void rejectsApprovalAfterOfferingStarts() {
        // given
        Offering offering = createReviewRequestedOffering();
        ReflectionTestUtils.setField(
                offering, "startAt", Instant.now().minusSeconds(1)
        );

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.approve(UUID.randomUUID())
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_PERIOD_EXPIRED);
    }

    @Test
    @DisplayName("심사 요청된 공모를 반려하면 REJECTED 상태로 전환된다")
    void rejectsReviewRequestedOffering() {
        // given
        Offering offering = createReviewRequestedOffering();
        UUID reviewerId = UUID.randomUUID();

        // when
        offering.reject(reviewerId, "  자산 증빙 부족  ");

        // then
        assertThat(offering.getOfferingStatus())
                .isEqualTo(OfferingStatus.REJECTED);
        assertThat(offering.getRejectionReason())
                .isEqualTo("자산 증빙 부족");
        assertThat(offering.getReviewedAt()).isNotNull();
        assertThat(offering.getReviewedBy()).isEqualTo(reviewerId);
    }

    @Test
    @DisplayName("REVIEW_REQUESTED 상태가 아니면 반려할 수 없다")
    void rejectsRejectionWhenNotReviewRequested() {
        // given
        Offering offering = createDraftOffering();

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.reject(UUID.randomUUID(), "사유")
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_REJECTION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("반려 사유가 없으면 반려할 수 없다")
    void rejectsRejectionWithoutReason() {
        // given
        Offering offering = createReviewRequestedOffering();

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.reject(UUID.randomUUID(), null)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_REJECTION_REASON);
    }

    @Test
    @DisplayName("반려 사유가 공백이면 반려할 수 없다")
    void rejectsRejectionWithBlankReason() {
        // given
        Offering offering = createReviewRequestedOffering();

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.reject(UUID.randomUUID(), "   ")
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_REJECTION_REASON);
    }

    @Test
    @DisplayName("반려 사유가 500자를 초과하면 반려할 수 없다")
    void rejectsRejectionWithTooLongReason() {
        // given
        Offering offering = createReviewRequestedOffering();
        String longReason = "가".repeat(501);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.reject(UUID.randomUUID(), longReason)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_REJECTION_REASON);
    }

    @Test
    @DisplayName("반려자 ID가 없으면 반려할 수 없다")
    void rejectsRejectionWithoutReviewerId() {
        // given
        Offering offering = createReviewRequestedOffering();

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.reject(null, "사유")
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_OFFERING_INPUT);
    }

    @Test
    @DisplayName("DRAFT 상태의 공모 정보를 수정하면 변경된 값이 반영된다")
    void updatesDraftOffering() {
        // given
        Offering offering = createDraftOffering();
        Instant newStart = Instant.now().plusSeconds(10_000);
        Instant newEnd = Instant.now().plusSeconds(20_000);

        // when
        offering.update(
                "변경된 제목", 200L, 2L, 150L, newStart, newEnd
        );

        // then
        assertThat(offering.getTitle()).isEqualTo("변경된 제목");
        assertThat(offering.getTotalQuantity()).isEqualTo(200L);
        assertThat(offering.getRemainingQuantity()).isEqualTo(200L);
        assertThat(offering.getMinSubscriptionQuantity()).isEqualTo(2L);
        assertThat(offering.getMaxSubscriptionQuantity()).isEqualTo(150L);
        assertThat(offering.getStartAt()).isEqualTo(newStart);
        assertThat(offering.getEndAt()).isEqualTo(newEnd);
    }

    @Test
    @DisplayName("일부 필드만 전달하면 기존 값이 유지된다")
    void updatesDraftOfferingWithPartialFields() {
        // given
        Offering offering = createDraftOffering();
        String originalTitle = offering.getTitle();

        // when
        offering.update(
                null, 150L, null, null, null, null
        );

        // then
        assertThat(offering.getTitle()).isEqualTo(originalTitle);
        assertThat(offering.getTotalQuantity()).isEqualTo(150L);
        assertThat(offering.getRemainingQuantity()).isEqualTo(150L);
    }

    @Test
    @DisplayName("DRAFT 상태가 아니면 공모 정보를 수정할 수 없다")
    void rejectsUpdateWhenNotDraft() {
        // given
        Offering offering = createReviewRequestedOffering();

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.update(
                        "변경된 제목", 200L, 2L, 150L,
                        Instant.now().plusSeconds(10_000),
                        Instant.now().plusSeconds(20_000)
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_UPDATE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("수정 결과 총 모집 수량이 0 이하이면 수정할 수 없다")
    void rejectsUpdateWithInvalidTotalQuantity() {
        // given
        Offering offering = createDraftOffering();

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.update(
                        null, 0L, null, null, null, null
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_OFFERING_QUANTITY);
    }

    @Test
    @DisplayName("DRAFT 상태의 공모를 삭제하면 논리 삭제된다")
    void deletesDraftOffering() {
        // given
        Offering offering = createDraftOffering();
        UUID deletedBy = UUID.randomUUID();

        // when
        offering.delete(deletedBy);

        // then
        assertThat(offering.isDeleted()).isTrue();
        assertThat(offering.getDeletedBy()).isEqualTo(deletedBy);
        assertThat(offering.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("DRAFT 상태가 아니면 삭제할 수 없다")
    void rejectsDeleteWhenNotDraft() {
        // given
        Offering offering = createReviewRequestedOffering();

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.delete(UUID.randomUUID())
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_DELETE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("삭제자 ID가 없으면 삭제할 수 없다")
    void rejectsDeleteWithoutDeletedBy() {
        // given
        Offering offering = createDraftOffering();

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.delete(null)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_OFFERING_INPUT);
    }

    private Offering createCancellingOfferingWithRestoredQuantity() {
        Offering offering = createExpiredOpenOffering(50L);
        ReflectionTestUtils.setField(
                offering, "remainingQuantity", offering.getTotalQuantity()
        );
        ReflectionTestUtils.setField(
                offering, "offeringStatus", OfferingStatus.CANCELLING
        );
        ReflectionTestUtils.setField(
                offering, "cancellationType", CancellationType.ADMIN_CANCELLED
        );
        return offering;
    }

    @Test
    @DisplayName("보상이 완료된 취소 공모는 CANCELLED로 완료된다")
    void completesCancellation() {
        // given
        Offering offering = createCancellingOfferingWithRestoredQuantity();
        Instant cancelledAt = Instant.now();

        // when
        offering.completeCancellation(cancelledAt);

        // then
        assertThat(offering.getOfferingStatus())
                .isEqualTo(OfferingStatus.CANCELLED);
        assertThat(offering.getCancelledAt()).isEqualTo(cancelledAt);
    }

    @Test
    @DisplayName("취소 완료 시각이 없으면 취소를 완료할 수 없다")
    void rejectsCompleteCancellationWithoutCancelledAt() {
        // given
        Offering offering = createCancellingOfferingWithRestoredQuantity();

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.completeCancellation(null)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_OFFERING_INPUT);
    }

    @Test
    @DisplayName("CANCELLING 상태가 아니면 취소를 완료할 수 없다")
    void rejectsCompleteCancellationWhenNotCancelling() {
        // given
        Offering offering = createDraftOffering();

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.completeCancellation(Instant.now())
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_CANCELLATION_COMPLETION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("취소 종류가 없으면 취소를 완료할 수 없다")
    void rejectsCompleteCancellationWithoutCancellationType() {
        // given
        Offering offering = createExpiredOpenOffering(50L);
        ReflectionTestUtils.setField(
                offering, "remainingQuantity", offering.getTotalQuantity()
        );
        ReflectionTestUtils.setField(
                offering, "offeringStatus", OfferingStatus.CANCELLING
        );

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.completeCancellation(Instant.now())
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_CANCELLATION_COMPLETION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("복원되지 않은 수량이 있으면 취소를 완료할 수 없다")
    void rejectsCompleteCancellationWithUnrestoredQuantity() {
        // given
        Offering offering = createExpiredOpenOffering(50L);
        ReflectionTestUtils.setField(
                offering, "offeringStatus", OfferingStatus.CANCELLING
        );
        ReflectionTestUtils.setField(
                offering, "cancellationType", CancellationType.ADMIN_CANCELLED
        );

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offering.completeCancellation(Instant.now())
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_QUANTITY_STATE_INVALID);
    }
}
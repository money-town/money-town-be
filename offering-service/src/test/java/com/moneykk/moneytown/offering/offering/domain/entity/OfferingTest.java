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
}
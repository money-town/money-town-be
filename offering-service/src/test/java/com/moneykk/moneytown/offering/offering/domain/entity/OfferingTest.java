package com.moneykk.moneytown.offering.offering.domain.entity;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
}
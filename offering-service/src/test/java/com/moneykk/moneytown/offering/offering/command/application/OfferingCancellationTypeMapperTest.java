package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfferingCancellationTypeMapperTest {

    @Test
    @DisplayName("관리자 중단 유형을 청약 관리자 중단 유형으로 변환한다")
    void mapsAdminCancellation() {
        Offering offering = mock(Offering.class);

        when(offering.getCancellationType())
                .thenReturn(
                        com.moneykk.moneytown.offering.offering.domain.entity
                                .CancellationType.ADMIN_CANCELLED
                );

        assertThat(
                OfferingCancellationTypeMapper
                        .toSubscriptionType(offering)
        ).isEqualTo(CancellationType.OFFERING_ADMIN_CANCELLED);
    }

    @Test
    @DisplayName("모집 미달 유형을 청약 모집 미달 유형으로 변환한다")
    void mapsUnderSubscribedCancellation() {
        Offering offering = mock(Offering.class);

        when(offering.getCancellationType())
                .thenReturn(
                        com.moneykk.moneytown.offering.offering.domain.entity
                                .CancellationType.UNDER_SUBSCRIBED
                );

        assertThat(
                OfferingCancellationTypeMapper
                        .toSubscriptionType(offering)
        ).isEqualTo(CancellationType.OFFERING_UNDER_SUBSCRIBED);
    }

    @Test
    @DisplayName("취소 처리 공모에 취소 유형이 없으면 실패한다")
    void rejectsMissingCancellationType() {
        Offering offering = mock(Offering.class);

        assertThatThrownBy(
                () -> OfferingCancellationTypeMapper
                        .toSubscriptionType(offering)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancellationType");
    }
}

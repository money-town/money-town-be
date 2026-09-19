package com.moneykk.moneytown.offering.offering.query.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfferingListItemResponseTest {

    @Test
    @DisplayName("Offering 엔티티의 필드를 목록 응답 필드에 그대로 매핑한다")
    void mapsOfferingFieldsToResponse() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Instant startAt = Instant.parse("2026-09-10T09:00:00Z");
        Instant endAt = Instant.parse("2026-09-17T09:00:00Z");

        Offering offering = mock(Offering.class);
        when(offering.getOfferingId()).thenReturn(offeringId);
        when(offering.getAssetId()).thenReturn(assetId);
        when(offering.getTitle()).thenReturn("강남 오피스텔 조각투자 1차 공모");
        when(offering.getPricePerUnit()).thenReturn(100_000L);
        when(offering.getTotalQuantity()).thenReturn(100_000L);
        when(offering.getRemainingQuantity()).thenReturn(7_600L);
        when(offering.getOfferingStatus()).thenReturn(OfferingStatus.OPEN);
        when(offering.getStartAt()).thenReturn(startAt);
        when(offering.getEndAt()).thenReturn(endAt);

        // when
        OfferingListItemResponse response =
                OfferingListItemResponse.from(offering);

        // then
        assertThat(response.offeringId()).isEqualTo(offeringId);
        assertThat(response.assetId()).isEqualTo(assetId);
        assertThat(response.title())
                .isEqualTo("강남 오피스텔 조각투자 1차 공모");
        assertThat(response.pricePerUnit()).isEqualTo(100_000L);
        assertThat(response.totalQuantity()).isEqualTo(100_000L);
        assertThat(response.remainingQuantity()).isEqualTo(7_600L);
        assertThat(response.offeringStatus()).isEqualTo(OfferingStatus.OPEN);
        assertThat(response.startAt()).isEqualTo(startAt);
        assertThat(response.endAt()).isEqualTo(endAt);
    }
}

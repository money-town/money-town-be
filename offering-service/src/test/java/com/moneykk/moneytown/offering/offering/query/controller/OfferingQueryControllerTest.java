package com.moneykk.moneytown.offering.offering.query.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.query.application.OfferingQueryService;
import com.moneykk.moneytown.offering.offering.query.dto.request.OfferingSearchCondition;
import com.moneykk.moneytown.offering.offering.query.dto.response.OfferingDetailResponse;
import com.moneykk.moneytown.offering.offering.query.dto.response.OfferingListItemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingQueryControllerTest {

    @Mock
    private OfferingQueryService offeringQueryService;

    @InjectMocks
    private OfferingQueryController offeringQueryController;

    private final Pageable pageable = PageRequest.of(0, 10);

    @Test
    @DisplayName("공개 공모 목록을 조회하면 200 OK와 목록을 반환한다")
    void searchesPublicOfferings() {
        // given
        OfferingListItemResponse item = new OfferingListItemResponse(
                UUID.randomUUID(), UUID.randomUUID(), "공모 상품",
                1_000L, 100L, 100L, OfferingStatus.OPEN,
                null, null
        );

        PageResponse<OfferingListItemResponse> response = new PageResponse<>(
                List.of(item), 0, 10, 1, 1, true, true, false
        );

        when(offeringQueryService.searchPublicOfferings(
                new OfferingSearchCondition(null, null), pageable
        )).thenReturn(response);

        // when
        ResponseEntity<ApiResponse<PageResponse<OfferingListItemResponse>>> result =
                offeringQueryController.searchPublicOfferings(
                        null, null, pageable
                );

        // then
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);
        assertThat(result.getBody().message())
                .isEqualTo("공개 공모 목록 조회가 완료되었습니다.");
    }

    @Test
    @DisplayName("조회된 공모가 없으면 안내 메시지를 반환한다")
    void returnsEmptyMessageWhenNoPublicOfferingsFound() {
        // given
        PageResponse<OfferingListItemResponse> response = new PageResponse<>(
                List.of(), 0, 10, 0, 0, true, true, false
        );

        when(offeringQueryService.searchPublicOfferings(
                new OfferingSearchCondition(null, null), pageable
        )).thenReturn(response);

        // when
        ResponseEntity<ApiResponse<PageResponse<OfferingListItemResponse>>> result =
                offeringQueryController.searchPublicOfferings(
                        null, null, pageable
                );

        // then
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().message())
                .isEqualTo("조회된 공모가 없습니다.");
    }

    @Test
    @DisplayName("ISSUER가 내 공모 목록을 조회하면 200 OK를 반환한다")
    void searchesMyOfferingsForIssuer() {
        // given
        UUID issuerId = UUID.randomUUID();

        PageResponse<OfferingListItemResponse> response = new PageResponse<>(
                List.of(), 0, 10, 0, 0, true, true, false
        );

        when(offeringQueryService.searchMyOfferings(
                issuerId, new OfferingSearchCondition(null, null), pageable
        )).thenReturn(response);

        // when
        ResponseEntity<ApiResponse<PageResponse<OfferingListItemResponse>>> result =
                offeringQueryController.searchMyOfferings(
                        issuerId, "ISSUER", null, null, pageable
                );

        // then
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().message())
                .isEqualTo("조회된 공모가 없습니다.");
    }

    @Test
    @DisplayName("ISSUER가 아니면 내 공모 목록 조회를 거부한다")
    void rejectsMyOfferingsForNonIssuer() {
        // when & then
        assertThatThrownBy(() ->
                offeringQueryController.searchMyOfferings(
                        UUID.randomUUID(), "ADMIN", null, null, pageable
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(OfferingErrorCode.OFFERING_ACCESS_DENIED)
                );

        verifyNoInteractions(offeringQueryService);
    }

    @Test
    @DisplayName("ADMIN이 관리자 공모 목록을 조회하면 200 OK를 반환한다")
    void searchesOfferingsForManagementAsAdmin() {
        // given
        OfferingListItemResponse item = new OfferingListItemResponse(
                UUID.randomUUID(), UUID.randomUUID(), "공모 상품",
                1_000L, 100L, 100L, OfferingStatus.DRAFT,
                null, null
        );

        PageResponse<OfferingListItemResponse> response = new PageResponse<>(
                List.of(item), 0, 10, 1, 1, true, true, false
        );

        when(offeringQueryService.searchOfferingsForManagement(
                new OfferingSearchCondition(null, null), pageable
        )).thenReturn(response);

        // when
        ResponseEntity<ApiResponse<PageResponse<OfferingListItemResponse>>> result =
                offeringQueryController.searchOfferingsForManagement(
                        "ADMIN", null, null, pageable
                );

        // then
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().message())
                .isEqualTo("관리자 공모 목록 조회가 완료되었습니다.");
    }

    @Test
    @DisplayName("ADMIN이 아니면 관리자 공모 목록 조회를 거부한다")
    void rejectsManagementSearchForNonAdmin() {
        // when & then
        assertThatThrownBy(() ->
                offeringQueryController.searchOfferingsForManagement(
                        "ISSUER", null, null, pageable
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(OfferingErrorCode.OFFERING_MANAGEMENT_ACCESS_DENIED)
                );

        verifyNoInteractions(offeringQueryService);
    }

    @Test
    @DisplayName("공모 상세 조회는 200 OK와 조회 결과를 반환한다")
    void getsOfferingDetail() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        OfferingDetailResponse response = new OfferingDetailResponse(
                offeringId, UUID.randomUUID(), null, "공모 상품",
                1_000L, 100L, 100L, 1L, 10L,
                null, null, OfferingStatus.OPEN, null, null, null
        );

        when(offeringQueryService.getOffering(offeringId, userId, "INVESTOR"))
                .thenReturn(response);

        // when
        ResponseEntity<ApiResponse<OfferingDetailResponse>> result =
                offeringQueryController.getOffering(
                        offeringId, userId, "INVESTOR"
                );

        // then
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);
        assertThat(result.getBody().message())
                .isEqualTo("공모 상품 상세 조회가 완료되었습니다.");

        verify(offeringQueryService)
                .getOffering(offeringId, userId, "INVESTOR");
    }
}

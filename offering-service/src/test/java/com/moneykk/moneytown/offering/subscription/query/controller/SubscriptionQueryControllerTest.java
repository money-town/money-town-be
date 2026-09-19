package com.moneykk.moneytown.offering.subscription.query.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.query.application.SubscriptionQueryService;
import com.moneykk.moneytown.offering.subscription.query.dto.request.SubscriptionSearchCondition;
import com.moneykk.moneytown.offering.subscription.query.dto.response.SubscriptionDetailResponse;
import com.moneykk.moneytown.offering.subscription.query.dto.response.SubscriptionListItemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionQueryControllerTest {

    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    @Mock
    private SubscriptionQueryService subscriptionQueryService;

    @InjectMocks
    private SubscriptionQueryController subscriptionQueryController;

    private final Pageable pageable = PageRequest.of(0, 10);

    @Test
    @DisplayName("INVESTOR가 내 청약 목록을 조회하면 200 OK를 반환하고 기간은 한국 시간 기준으로 변환한다")
    void searchesMySubscriptionsForInvestor() {
        // given
        UUID userId = UUID.randomUUID();

        LocalDateTime startDate = LocalDateTime.of(2026, 9, 10, 9, 0);
        LocalDateTime endDate = LocalDateTime.of(2026, 9, 10, 18, 0);

        Instant expectedStart = startDate.atZone(SERVICE_ZONE_ID).toInstant();
        Instant expectedEnd = endDate.atZone(SERVICE_ZONE_ID).toInstant();

        SubscriptionListItemResponse item = new SubscriptionListItemResponse(
                UUID.randomUUID(), UUID.randomUUID(), 10L, 1_000L, 10_000L,
                SubscriptionStatus.CONFIRMED, null, null, null,
                Instant.now(), Instant.now()
        );

        PageResponse<SubscriptionListItemResponse> response = new PageResponse<>(
                List.of(item), 0, 10, 1, 1, true, true, false
        );

        ArgumentCaptor<SubscriptionSearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(SubscriptionSearchCondition.class);

        when(subscriptionQueryService.searchMySubscriptions(
                eq(userId), conditionCaptor.capture(), eq(pageable)
        )).thenReturn(response);

        // when
        ResponseEntity<ApiResponse<PageResponse<SubscriptionListItemResponse>>> result =
                subscriptionQueryController.searchMySubscriptions(
                        userId, "INVESTOR", null, null,
                        startDate, endDate, pageable
                );

        // then
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);
        assertThat(result.getBody().message())
                .isEqualTo("내 청약 목록 조회가 완료되었습니다.");

        assertThat(conditionCaptor.getValue().startDate())
                .isEqualTo(expectedStart);
        assertThat(conditionCaptor.getValue().endDate())
                .isEqualTo(expectedEnd);
    }

    @Test
    @DisplayName("조회된 청약 내역이 없으면 안내 메시지를 반환한다")
    void returnsEmptyMessageWhenNoSubscriptionsFound() {
        // given
        UUID userId = UUID.randomUUID();

        PageResponse<SubscriptionListItemResponse> response = new PageResponse<>(
                List.of(), 0, 10, 0, 0, true, true, false
        );

        when(subscriptionQueryService.searchMySubscriptions(
                eq(userId), any(SubscriptionSearchCondition.class), eq(pageable)
        )).thenReturn(response);

        // when
        ResponseEntity<ApiResponse<PageResponse<SubscriptionListItemResponse>>> result =
                subscriptionQueryController.searchMySubscriptions(
                        userId, "INVESTOR", null, null,
                        null, null, pageable
                );

        // then
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().message())
                .isEqualTo("조회된 청약 내역이 없습니다.");
    }

    @Test
    @DisplayName("INVESTOR가 아니면 내 청약 목록 조회를 거부한다")
    void rejectsMySubscriptionsForNonInvestor() {
        // when & then
        assertThatThrownBy(() ->
                subscriptionQueryController.searchMySubscriptions(
                        UUID.randomUUID(), "ADMIN", null, null,
                        null, null, pageable
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(SubscriptionErrorCode.SUBSCRIPTION_ACCESS_DENIED)
                );

        verifyNoInteractions(subscriptionQueryService);
    }

    @Test
    @DisplayName("INVESTOR는 청약 상세를 조회할 수 있다")
    void getsSubscriptionDetailForInvestor() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SubscriptionDetailResponse response = new SubscriptionDetailResponse(
                subscriptionId, UUID.randomUUID(), 10L, 1_000L, 10_000L,
                SubscriptionStatus.CONFIRMED, null, null, null,
                null, null, Instant.now(), Instant.now()
        );

        when(subscriptionQueryService.getSubscriptionDetail(
                subscriptionId, userId, "INVESTOR"
        )).thenReturn(response);

        // when
        ResponseEntity<ApiResponse<SubscriptionDetailResponse>> result =
                subscriptionQueryController.getSubscriptionDetail(
                        subscriptionId, userId, "INVESTOR"
                );

        // then
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);

        verify(subscriptionQueryService)
                .getSubscriptionDetail(subscriptionId, userId, "INVESTOR");
    }

    @Test
    @DisplayName("ADMIN은 청약 상세를 조회할 수 있다")
    void getsSubscriptionDetailForAdmin() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        SubscriptionDetailResponse response = new SubscriptionDetailResponse(
                subscriptionId, UUID.randomUUID(), 10L, 1_000L, 10_000L,
                SubscriptionStatus.CONFIRMED, null, null, null,
                null, null, Instant.now(), Instant.now()
        );

        when(subscriptionQueryService.getSubscriptionDetail(
                subscriptionId, adminId, "ADMIN"
        )).thenReturn(response);

        // when
        ResponseEntity<ApiResponse<SubscriptionDetailResponse>> result =
                subscriptionQueryController.getSubscriptionDetail(
                        subscriptionId, adminId, "ADMIN"
                );

        // then
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    @DisplayName("INVESTOR와 ADMIN이 아니면 청약 상세 조회를 거부한다")
    void rejectsSubscriptionDetailForOtherRoles() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() ->
                subscriptionQueryController.getSubscriptionDetail(
                        subscriptionId, userId, "ISSUER"
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(SubscriptionErrorCode.SUBSCRIPTION_ACCESS_DENIED)
                );

        verifyNoInteractions(subscriptionQueryService);
    }
}

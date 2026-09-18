package com.moneykk.moneytown.offering.offering.query.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.query.application.OfferingQueryService;
import com.moneykk.moneytown.offering.offering.query.dto.response.AiPortfolioCandidateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingInternalQueryControllerTest {

    @Mock
    private OfferingQueryService offeringQueryService;

    @InjectMocks
    private OfferingInternalQueryController
            offeringInternalQueryController;

    @Test
    @DisplayName("SYSTEM 권한은 AI 포트폴리오 공모 후보를 조회할 수 있다")
    void returnsAiPortfolioCandidatesForSystem() {
        // given
        int limit = 10;

        AiPortfolioCandidateResponse candidate =
                new AiPortfolioCandidateResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "강남 오피스텔 조각투자 1차 공모",
                        100_000L,
                        100_000L,
                        7_600L,
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-20T09:00:00Z")
                );

        when(offeringQueryService.getAiPortfolioCandidates(limit))
                .thenReturn(List.of(candidate));

        // when
        ResponseEntity<
                ApiResponse<List<AiPortfolioCandidateResponse>>
                > response =
                offeringInternalQueryController
                        .getAiPortfolioCandidates(
                                "SYSTEM",
                                limit
                        );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody()).isNotNull();

        ApiResponse<List<AiPortfolioCandidateResponse>> body =
                response.getBody();

        assertThat(body.success()).isTrue();
        assertThat(body.code()).isNull();

        assertThat(body.message())
                .isEqualTo(
                        "AI 포트폴리오 공모 후보 조회가 완료되었습니다."
                );

        assertThat(body.data())
                .containsExactly(candidate);

        verify(offeringQueryService)
                .getAiPortfolioCandidates(limit);
    }

    @Test
    @DisplayName("AI 포트폴리오 공모 후보가 없으면 빈 목록을 반환한다")
    void returnsEmptyAiPortfolioCandidateList() {
        // given
        int limit = 10;

        when(offeringQueryService.getAiPortfolioCandidates(limit))
                .thenReturn(List.of());

        // when
        ResponseEntity<
                ApiResponse<List<AiPortfolioCandidateResponse>>
                > response =
                offeringInternalQueryController
                        .getAiPortfolioCandidates(
                                "SYSTEM",
                                limit
                        );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody()).isNotNull();

        ApiResponse<List<AiPortfolioCandidateResponse>> body =
                response.getBody();

        assertThat(body.success()).isTrue();
        assertThat(body.code()).isNull();
        assertThat(body.data()).isEmpty();

        assertThat(body.message())
                .isEqualTo(
                        "조회된 AI 포트폴리오 공모 후보가 없습니다."
                );

        verify(offeringQueryService)
                .getAiPortfolioCandidates(limit);
    }

    @Test
    @DisplayName("SYSTEM 권한이 아니면 AI 포트폴리오 공모 후보 조회를 거부한다")
    void rejectsAiPortfolioCandidateRequestForNonSystemRole() {
        // given
        int limit = 10;

        // when & then
        assertThatThrownBy(() ->
                offeringInternalQueryController
                        .getAiPortfolioCandidates(
                                "INVESTOR",
                                limit
                        )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                OfferingErrorCode.OFFERING_ACCESS_DENIED
                        )
                );

        verifyNoInteractions(offeringQueryService);
    }

    @Test
    @DisplayName("권한 헤더가 없으면 AI 포트폴리오 공모 후보 조회를 거부한다")
    void rejectsAiPortfolioCandidateRequestWithoutRole() {
        // given
        int limit = 10;

        // when & then
        assertThatThrownBy(() ->
                offeringInternalQueryController
                        .getAiPortfolioCandidates(
                                null,
                                limit
                        )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                OfferingErrorCode.OFFERING_ACCESS_DENIED
                        )
                );

        verifyNoInteractions(offeringQueryService);
    }

    @Test
    @DisplayName("SYSTEM 권한은 대소문자가 정확히 일치해야 한다")
    void rejectsLowercaseSystemRole() {
        // given
        int limit = 10;

        // when & then
        assertThatThrownBy(() ->
                offeringInternalQueryController
                        .getAiPortfolioCandidates(
                                "system",
                                limit
                        )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                OfferingErrorCode.OFFERING_ACCESS_DENIED
                        )
                );

        verifyNoInteractions(offeringQueryService);
    }
}
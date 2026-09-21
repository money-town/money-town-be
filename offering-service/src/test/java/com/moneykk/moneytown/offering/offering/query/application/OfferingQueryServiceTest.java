package com.moneykk.moneytown.offering.offering.query.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.offering.query.dto.request.OfferingSearchCondition;
import com.moneykk.moneytown.offering.offering.query.dto.response.AiPortfolioCandidateResponse;
import com.moneykk.moneytown.offering.offering.query.dto.response.OfferingDetailResponse;
import com.moneykk.moneytown.offering.offering.query.dto.response.OfferingListItemResponse;
import com.moneykk.moneytown.offering.offering.query.repository.OfferingQueryRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingQueryServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private OfferingQueryRepository offeringQueryRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private OfferingQueryService offeringQueryService;

    @Test
    @DisplayName("INVESTOR는 공개 공모의 관리용 필드를 조회할 수 없다")
    void doesNotExposePrivateFieldsToInvestor() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID investorId = UUID.randomUUID();

        Offering offering =
                offering(OfferingStatus.OPEN);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when
        OfferingDetailResponse response =
                offeringQueryService.getOffering(
                        offeringId,
                        investorId,
                        "INVESTOR"
                );

        // then
        assertThat(response.issuerId()).isNull();
        assertThat(response.cancellationType()).isNull();

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("INVESTOR는 비공개 공모를 조회할 수 없다")
    void deniesPrivateOfferingToInvestor() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID investorId = UUID.randomUUID();

        Offering offering =
                offering(OfferingStatus.DRAFT);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringQueryService.getOffering(
                        offeringId,
                        investorId,
                        "INVESTOR"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        OfferingErrorCode.OFFERING_ACCESS_DENIED
                );

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("소유 ISSUER는 자신의 비공개 공모를 조회할 수 있다")
    void allowsPrivateOfferingToOwnerIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();

        Offering offering =
                offering(OfferingStatus.DRAFT);

        when(offering.getIssuerId())
                .thenReturn(issuerId);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when
        OfferingDetailResponse response =
                offeringQueryService.getOffering(
                        offeringId,
                        issuerId,
                        "ISSUER"
                );

        // then
        assertThat(response.issuerId()).isEqualTo(issuerId);

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("ADMIN은 다른 사용자의 비공개 공모를 조회할 수 있다")
    void allowsPrivateOfferingToAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Offering offering =
                offering(OfferingStatus.REVIEW_REQUESTED);

        when(offering.getIssuerId())
                .thenReturn(issuerId);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when
        OfferingDetailResponse response =
                offeringQueryService.getOffering(
                        offeringId,
                        adminId,
                        "ADMIN"
                );

        // then
        assertThat(response.issuerId()).isEqualTo(issuerId);

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 공개 공모를 조회할 수 있지만 관리용 필드는 제외된다")
    void allowsAnonymousPublicOfferingWithoutPrivateFields() {
        // given
        UUID offeringId = UUID.randomUUID();

        Offering offering =
                offering(OfferingStatus.OPEN);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when
        OfferingDetailResponse response =
                offeringQueryService.getOffering(
                        offeringId,
                        null,
                        null
                );

        // then
        assertThat(response.issuerId()).isNull();
        assertThat(response.cancellationType()).isNull();

        verifyNoInteractions(subscriptionRepository);
    }

    private Offering offering(OfferingStatus status) {
        Offering offering = mock(Offering.class);

        when(offering.getOfferingStatus())
                .thenReturn(status);

        return offering;
    }

    @Test
    @DisplayName("AI 포트폴리오 공모 후보를 지정된 개수만큼 조회한다")
    void getsAiPortfolioCandidates() {
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

        when(offeringQueryRepository.findAiPortfolioCandidates(
                any(Instant.class),
                eq(limit)
        )).thenReturn(List.of(candidate));

        // when
        List<AiPortfolioCandidateResponse> response =
                offeringQueryService.getAiPortfolioCandidates(limit);

        // then
        assertThat(response)
                .containsExactly(candidate);

        verify(offeringQueryRepository)
                .findAiPortfolioCandidates(
                        any(Instant.class),
                        eq(limit)
                );

        verifyNoInteractions(
                offeringRepository,
                subscriptionRepository
        );
    }

    @Test
    @DisplayName("AI 포트폴리오 공모 후보가 없으면 빈 목록을 반환한다")
    void returnsEmptyAiPortfolioCandidateList() {
        // given
        int limit = 10;

        when(offeringQueryRepository.findAiPortfolioCandidates(
                any(Instant.class),
                eq(limit)
        )).thenReturn(List.of());

        // when
        List<AiPortfolioCandidateResponse> response =
                offeringQueryService.getAiPortfolioCandidates(limit);

        // then
        assertThat(response).isEmpty();

        verify(offeringQueryRepository)
                .findAiPortfolioCandidates(
                        any(Instant.class),
                        eq(limit)
                );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 21})
    @DisplayName("AI 포트폴리오 공모 후보 조회 개수가 허용 범위를 벗어나면 실패한다")
    void rejectsInvalidAiPortfolioCandidateLimit(int limit) {
        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringQueryService.getAiPortfolioCandidates(limit)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        OfferingErrorCode.INVALID_OFFERING_SEARCH_CONDITION
                );

        verifyNoInteractions(
                offeringRepository,
                offeringQueryRepository,
                subscriptionRepository
        );
    }

    @Test
    @DisplayName("공개 공모 목록을 정상적으로 검색한다")
    void searchesPublicOfferingsSuccessfully() {
        // given
        OfferingSearchCondition condition =
                new OfferingSearchCondition(OfferingStatus.OPEN, null);
        Pageable pageable = PageRequest.of(0, 10);

        when(offeringQueryRepository.searchPublicOfferingsContent(
                condition, pageable
        )).thenReturn(List.of());

        when(offeringQueryRepository.countPublicOfferings(
                condition
        )).thenReturn(0L);

        // when
        PageResponse<OfferingListItemResponse> response =
                offeringQueryService.searchPublicOfferings(
                        condition, pageable
                );

        // then
        assertThat(response.content()).isEmpty();

        verify(offeringQueryRepository)
                .searchPublicOfferingsContent(condition, pageable);
        verify(offeringQueryRepository)
                .countPublicOfferings(condition);
    }

    @Test
    @DisplayName("비공개 상태를 공개 목록 검색 조건으로 사용하면 오류로 처리한다")
    void rejectsPrivateStatusInPublicSearchCondition() {
        // given
        OfferingSearchCondition condition =
                new OfferingSearchCondition(OfferingStatus.DRAFT, null);
        Pageable pageable = PageRequest.of(0, 10);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringQueryService.searchPublicOfferings(
                        condition, pageable
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        OfferingErrorCode.INVALID_OFFERING_SEARCH_CONDITION
                );

        verifyNoInteractions(offeringQueryRepository);
    }

    @Test
    @DisplayName("내 공모 목록을 검색한다")
    void searchesMyOfferings() {
        // given
        UUID issuerId = UUID.randomUUID();
        OfferingSearchCondition condition =
                new OfferingSearchCondition(null, null);
        Pageable pageable = PageRequest.of(0, 10);

        when(offeringQueryRepository.searchMyOfferings(
                issuerId, condition, pageable
        )).thenReturn(new PageImpl<>(List.of()));

        // when
        PageResponse<OfferingListItemResponse> response =
                offeringQueryService.searchMyOfferings(
                        issuerId, condition, pageable
                );

        // then
        assertThat(response.content()).isEmpty();

        verify(offeringQueryRepository)
                .searchMyOfferings(issuerId, condition, pageable);
    }

    @Test
    @DisplayName("관리자용 공모 목록을 검색한다")
    void searchesOfferingsForManagement() {
        // given
        OfferingSearchCondition condition =
                new OfferingSearchCondition(null, null);
        Pageable pageable = PageRequest.of(0, 10);

        when(offeringQueryRepository.searchOfferingsForManagement(
                condition, pageable
        )).thenReturn(new PageImpl<>(List.of()));

        // when
        PageResponse<OfferingListItemResponse> response =
                offeringQueryService.searchOfferingsForManagement(
                        condition, pageable
                );

        // then
        assertThat(response.content()).isEmpty();

        verify(offeringQueryRepository)
                .searchOfferingsForManagement(condition, pageable);
    }

    @Test
    @DisplayName("존재하지 않거나 삭제된 공모는 조회할 수 없다")
    void throwsNotFoundWhenOfferingDoesNotExist() {
        // given
        UUID offeringId = UUID.randomUUID();

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringQueryService.getOffering(
                        offeringId, UUID.randomUUID(), "ADMIN"
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_NOT_FOUND);
    }

    @Test
    @DisplayName("소유 ISSUER는 취소된 공모의 관리용 상세를 조회할 수 있다")
    void allowsCancelledOfferingToOwnerIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();

        Offering offering = offering(OfferingStatus.CANCELLED);
        when(offering.getIssuerId()).thenReturn(issuerId);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when
        OfferingDetailResponse response = offeringQueryService.getOffering(
                offeringId, issuerId, "ISSUER"
        );

        // then
        assertThat(response.issuerId()).isEqualTo(issuerId);

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("ADMIN은 취소된 공모의 관리용 상세를 조회할 수 있다")
    void allowsCancelledOfferingToAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Offering offering = offering(OfferingStatus.CANCELLED);
        when(offering.getIssuerId()).thenReturn(issuerId);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when
        OfferingDetailResponse response = offeringQueryService.getOffering(
                offeringId, adminId, "ADMIN"
        );

        // then
        assertThat(response.issuerId()).isEqualTo(issuerId);

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("보상 완료된 투자자는 취소된 공모의 상세를 관리용 필드 없이 조회할 수 있다")
    void allowsCancelledOfferingToCompensatedInvestor() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID investorId = UUID.randomUUID();

        Offering offering = offering(OfferingStatus.CANCELLED);
        when(offering.getOfferingId()).thenReturn(offeringId);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(subscriptionRepository
                .existsByOfferingIdAndUserIdAndSubscriptionStatusAndCancellationTypeIsNotNullAndIsDeletedFalse(
                        offeringId, investorId, SubscriptionStatus.CANCELLED
                ))
                .thenReturn(true);

        // when
        OfferingDetailResponse response = offeringQueryService.getOffering(
                offeringId, investorId, "INVESTOR"
        );

        // then
        assertThat(response.issuerId()).isNull();
    }

    @Test
    @DisplayName("보상받지 않은 투자자는 취소된 공모의 상세를 조회할 수 없다")
    void deniesCancelledOfferingToNonCompensatedInvestor() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID investorId = UUID.randomUUID();

        Offering offering = offering(OfferingStatus.CANCELLED);
        when(offering.getOfferingId()).thenReturn(offeringId);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(subscriptionRepository
                .existsByOfferingIdAndUserIdAndSubscriptionStatusAndCancellationTypeIsNotNullAndIsDeletedFalse(
                        offeringId, investorId, SubscriptionStatus.CANCELLED
                ))
                .thenReturn(false);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringQueryService.getOffering(
                        offeringId, investorId, "INVESTOR"
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_ACCESS_DENIED);
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 취소된 공모의 상세를 조회할 수 없다")
    void deniesCancelledOfferingToAnonymousUser() {
        // given
        UUID offeringId = UUID.randomUUID();

        Offering offering = offering(OfferingStatus.CANCELLED);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringQueryService.getOffering(
                        offeringId, null, null
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_ACCESS_DENIED);

        verifyNoInteractions(subscriptionRepository);
    }
}
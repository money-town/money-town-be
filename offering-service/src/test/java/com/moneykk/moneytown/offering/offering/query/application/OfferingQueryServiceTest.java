package com.moneykk.moneytown.offering.offering.query.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.offering.query.dto.response.AiPortfolioCandidateResponse;
import com.moneykk.moneytown.offering.offering.query.dto.response.OfferingDetailResponse;
import com.moneykk.moneytown.offering.offering.query.repository.OfferingQueryRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
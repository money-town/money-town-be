package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingCreateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingUpdateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCreateResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingDeleteResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingReviewRequestResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingUpdateResponse;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingTransactionServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private OfferingTransactionService offeringTransactionService;

    @Test
    @DisplayName("공모를 DRAFT 상태로 생성하고 저장한다")
    void createsOffering() {
        // given
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        OfferingCreateRequest request =
                new OfferingCreateRequest(
                        assetId,
                        "테스트 공모",
                        100L,
                        1L,
                        10L,
                        now.plusHours(1),
                        now.plusHours(2)
                );

        when(offeringRepository.save(any(Offering.class)))
                .thenAnswer(invocation -> {
                    Offering offering =
                            invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            offering,
                            "offeringId",
                            offeringId
                    );

                    return offering;
                });

        // when
        OfferingCreateResponse response =
                offeringTransactionService.createOffering(
                        issuerId,
                        request,
                        10_000L
                );

        // then
        assertThat(response.offeringId()).isEqualTo(offeringId);
        assertThat(response.assetId()).isEqualTo(assetId);
        assertThat(response.title()).isEqualTo("테스트 공모");
        assertThat(response.offeringStatus())
                .isEqualTo(OfferingStatus.DRAFT);
        assertThat(response.totalQuantity()).isEqualTo(100L);
        assertThat(response.remainingQuantity()).isEqualTo(100L);

        ArgumentCaptor<Offering> offeringCaptor =
                ArgumentCaptor.forClass(Offering.class);

        verify(offeringRepository).save(offeringCaptor.capture());

        Offering savedOffering = offeringCaptor.getValue();

        assertThat(savedOffering.getStartAt())
                .isEqualTo(
                        request.startAt()
                                .atZone(ZoneId.of("Asia/Seoul"))
                                .toInstant()
                );

        assertThat(savedOffering.getEndAt())
                .isEqualTo(
                        request.endAt()
                                .atZone(ZoneId.of("Asia/Seoul"))
                                .toInstant()
                );

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("공모 소유 ISSUER는 DRAFT 공모를 수정할 수 있다")
    void updatesOfferingByOwnerIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();

        Offering offering =
                createDraftOffering(offeringId, issuerId);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        OfferingUpdateRequest request =
                updateRequest();

        // when
        OfferingUpdateResponse response =
                offeringTransactionService.updateOffering(
                        offeringId,
                        issuerId,
                        "ISSUER",
                        request
                );

        // then
        assertThat(response.offeringId()).isEqualTo(offeringId);
        assertThat(response.title()).isEqualTo("수정된 공모");
        assertThat(response.totalQuantity()).isEqualTo(200L);
        assertThat(response.remainingQuantity()).isEqualTo(200L);
        assertThat(response.minSubscriptionQuantity()).isEqualTo(2L);
        assertThat(response.maxSubscriptionQuantity()).isEqualTo(20L);
        assertThat(response.offeringStatus())
                .isEqualTo(OfferingStatus.DRAFT);

        verify(offeringRepository)
                .findByIdForUpdate(offeringId);

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("ADMIN은 공모 소유자가 아니어도 DRAFT 공모를 수정할 수 있다")
    void updatesOfferingByAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Offering offering =
                createDraftOffering(offeringId, issuerId);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        OfferingUpdateRequest request =
                updateRequest();

        // when
        OfferingUpdateResponse response =
                offeringTransactionService.updateOffering(
                        offeringId,
                        adminId,
                        "ADMIN",
                        request
                );

        // then
        assertThat(response.title()).isEqualTo("수정된 공모");
        assertThat(response.totalQuantity()).isEqualTo(200L);

        verify(offeringRepository)
                .findByIdForUpdate(offeringId);

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("공모 소유자가 아닌 ISSUER는 공모를 수정할 수 없다")
    void rejectsUpdateByNonOwnerIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID anotherIssuerId = UUID.randomUUID();

        Offering offering =
                createDraftOffering(offeringId, issuerId);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringTransactionService.updateOffering(
                        offeringId,
                        anotherIssuerId,
                        "ISSUER",
                        updateRequest()
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_ACCESS_DENIED);

        assertThat(offering.getTitle()).isEqualTo("테스트 공모");

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("공모 소유 ISSUER는 DRAFT 공모의 심사를 요청할 수 있다")
    void requestsReviewByOwnerIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();

        Offering offering =
                createDraftOffering(offeringId, issuerId);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        // when
        OfferingReviewRequestResponse response =
                offeringTransactionService.requestReview(
                        offeringId,
                        issuerId
                );

        // then
        assertThat(response.offeringId()).isEqualTo(offeringId);
        assertThat(response.offeringStatus())
                .isEqualTo(OfferingStatus.REVIEW_REQUESTED);
        assertThat(response.reviewRequestedAt()).isNotNull();

        assertThat(offering.getOfferingStatus())
                .isEqualTo(OfferingStatus.REVIEW_REQUESTED);

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("공모 소유자가 아닌 ISSUER는 심사를 요청할 수 없다")
    void rejectsReviewRequestByNonOwnerIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID anotherIssuerId = UUID.randomUUID();

        Offering offering =
                createDraftOffering(offeringId, issuerId);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringTransactionService.requestReview(
                        offeringId,
                        anotherIssuerId
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_ACCESS_DENIED);

        assertThat(offering.getOfferingStatus())
                .isEqualTo(OfferingStatus.DRAFT);

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("공모 소유 ISSUER는 청약 이력이 없는 DRAFT 공모를 삭제할 수 있다")
    void deletesOfferingByOwnerIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();

        Offering offering =
                createDraftOffering(offeringId, issuerId);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        when(subscriptionRepository.existsByOfferingId(offeringId))
                .thenReturn(false);

        // when
        OfferingDeleteResponse response =
                offeringTransactionService.deleteOffering(
                        offeringId,
                        issuerId,
                        "ISSUER"
                );

        // then
        assertThat(response.offeringId()).isEqualTo(offeringId);
        assertThat(offering.isDeleted()).isTrue();
        assertThat(offering.getDeletedBy()).isEqualTo(issuerId);
        assertThat(offering.getDeletedAt()).isNotNull();

        verify(subscriptionRepository)
                .existsByOfferingId(offeringId);
    }

    @Test
    @DisplayName("ADMIN은 청약 이력이 없는 DRAFT 공모를 삭제할 수 있다")
    void deletesOfferingByAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Offering offering =
                createDraftOffering(offeringId, issuerId);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        when(subscriptionRepository.existsByOfferingId(offeringId))
                .thenReturn(false);

        // when
        OfferingDeleteResponse response =
                offeringTransactionService.deleteOffering(
                        offeringId,
                        adminId,
                        "ADMIN"
                );

        // then
        assertThat(response.offeringId()).isEqualTo(offeringId);
        assertThat(offering.isDeleted()).isTrue();
        assertThat(offering.getDeletedBy()).isEqualTo(adminId);
        assertThat(offering.getDeletedAt()).isNotNull();

        verify(subscriptionRepository)
                .existsByOfferingId(offeringId);
    }

    @Test
    @DisplayName("공모 소유자가 아닌 ISSUER는 공모를 삭제할 수 없다")
    void rejectsDeletionByNonOwnerIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID anotherIssuerId = UUID.randomUUID();

        Offering offering =
                createDraftOffering(offeringId, issuerId);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringTransactionService.deleteOffering(
                        offeringId,
                        anotherIssuerId,
                        "ISSUER"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_ACCESS_DENIED);

        assertThat(offering.isDeleted()).isFalse();

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("청약 이력이 있는 공모는 삭제할 수 없다")
    void rejectsDeletionWhenSubscriptionExists() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();

        Offering offering =
                createDraftOffering(offeringId, issuerId);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        when(subscriptionRepository.existsByOfferingId(offeringId))
                .thenReturn(true);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringTransactionService.deleteOffering(
                        offeringId,
                        issuerId,
                        "ISSUER"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        OfferingErrorCode.OFFERING_HAS_SUBSCRIPTIONS
                );

        assertThat(offering.isDeleted()).isFalse();

        verify(subscriptionRepository)
                .existsByOfferingId(offeringId);
    }

    @Test
    @DisplayName("잠금 조회한 공모가 없으면 공모 없음 오류가 발생한다")
    void rejectsCommandWhenOfferingDoesNotExist() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringTransactionService.requestReview(
                        offeringId,
                        issuerId
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_NOT_FOUND);

        verifyNoInteractions(subscriptionRepository);
    }

    private Offering createDraftOffering(
            UUID offeringId,
            UUID issuerId
    ) {
        Instant now = Instant.now();

        Offering offering = Offering.create(
                UUID.randomUUID(),
                issuerId,
                "테스트 공모",
                10_000L,
                100L,
                1L,
                10L,
                now.plusSeconds(3_600),
                now.plusSeconds(7_200)
        );

        ReflectionTestUtils.setField(
                offering,
                "offeringId",
                offeringId
        );

        return offering;
    }

    private OfferingUpdateRequest updateRequest() {
        LocalDateTime now = LocalDateTime.now();

        return new OfferingUpdateRequest(
                "수정된 공모",
                200L,
                2L,
                20L,
                now.plusHours(2),
                now.plusHours(3)
        );
    }
}
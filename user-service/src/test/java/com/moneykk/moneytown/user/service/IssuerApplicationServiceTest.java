package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.user.dto.request.IssuerApplyRequest;
import com.moneykk.moneytown.user.dto.request.IssuerRejectRequest;
import com.moneykk.moneytown.user.dto.response.IssuerApplicationResponse;
import com.moneykk.moneytown.user.entity.IssuerApplication;
import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.entity.type.IssuerApplicationStatus;
import com.moneykk.moneytown.user.entity.type.UserRole;
import com.moneykk.moneytown.user.global.exception.IssuerApplicationErrorCode;
import com.moneykk.moneytown.user.repository.IssuerApplicationRepository;
import com.moneykk.moneytown.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class IssuerApplicationServiceTest {

    @Mock
    private IssuerApplicationRepository issuerApplicationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private IssuerApplicationService issuerApplicationService;

    @Test
    @DisplayName("로그인 사용자가 발행자 권한을 신청한다")
    void applyIssuerRole() {
        UUID userId = UUID.randomUUID();
        User user = createInvestor();
        IssuerApplyRequest request = new IssuerApplyRequest("보유 자산 공모 신청");

        given(userRepository.findByUserIdForUpdate(userId))
                .willReturn(Optional.of(user));
        given(issuerApplicationRepository
                .existsByUserIdAndStatusAndIsDeletedFalse(
                        userId,
                        IssuerApplicationStatus.PENDING
                ))
                .willReturn(false);
        given(issuerApplicationRepository.save(any(IssuerApplication.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        IssuerApplicationResponse response =
                issuerApplicationService.apply(userId, request);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.applicationReason()).isEqualTo("보유 자산 공모 신청");
        assertThat(response.status()).isEqualTo(IssuerApplicationStatus.PENDING);
        assertThat(response.appliedAt()).isNotNull();
    }

    @Test
    @DisplayName("가장 최근의 내 발행자 권한 신청을 조회한다")
    void getCurrentIssuerApplication() {
        UUID userId = UUID.randomUUID();
        IssuerApplication application =
                IssuerApplication.create(userId, "최근 신청");

        given(issuerApplicationRepository
                .findFirstByUserIdAndIsDeletedFalseOrderByAppliedAtDesc(userId))
                .willReturn(Optional.of(application));

        IssuerApplicationResponse response =
                issuerApplicationService.getCurrent(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.applicationReason()).isEqualTo("최근 신청");
        assertThat(response.status()).isEqualTo(IssuerApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("심사 중인 신청이 있으면 중복 신청을 거절한다")
    void rejectDuplicatePendingApplication() {
        UUID userId = UUID.randomUUID();
        User user = createInvestor();
        IssuerApplyRequest request = new IssuerApplyRequest("중복 신청");

        given(userRepository.findByUserIdForUpdate(userId))
                .willReturn(Optional.of(user));
        given(issuerApplicationRepository
                .existsByUserIdAndStatusAndIsDeletedFalse(
                        userId,
                        IssuerApplicationStatus.PENDING
                ))
                .willReturn(true);

        assertThatThrownBy(() ->
                issuerApplicationService.apply(userId, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        IssuerApplicationErrorCode
                                                .APPLICATION_ALREADY_PENDING
                                )
                );

        then(issuerApplicationRepository)
                .should(never())
                .save(any(IssuerApplication.class));
    }

    @Test
    @DisplayName("관리자가 신청을 승인하면 신청 상태와 사용자 권한이 변경된다")
    void approveIssuerApplication() {
        UUID adminId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        User admin = createAdmin();
        User applicant = createInvestor();
        IssuerApplication application =
                IssuerApplication.create(applicantId, "승인 신청");

        given(userRepository.findByUserIdAndIsDeletedFalse(adminId))
                .willReturn(Optional.of(admin));
        given(issuerApplicationRepository
                .findUserIdByApplicationId(applicationId))
                .willReturn(Optional.of(applicantId));
        given(userRepository.findByUserIdForUpdate(applicantId))
                .willReturn(Optional.of(applicant));
        given(issuerApplicationRepository.findByIdForUpdate(applicationId))
                .willReturn(Optional.of(application));

        IssuerApplicationResponse response =
                issuerApplicationService.approve(adminId, applicationId);

        assertThat(response.status())
                .isEqualTo(IssuerApplicationStatus.APPROVED);
        assertThat(response.reviewedBy()).isEqualTo(adminId);
        assertThat(response.reviewedAt()).isNotNull();
        assertThat(applicant.getRole()).isEqualTo(UserRole.ISSUER);
    }

    @Test
    @DisplayName("관리자가 신청을 거절하면 거절 사유와 심사 정보가 기록된다")
    void rejectIssuerApplication() {
        UUID adminId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        User admin = createAdmin();
        User applicant = createInvestor();
        IssuerApplication application =
                IssuerApplication.create(applicantId, "거절 신청");
        IssuerRejectRequest request =
                new IssuerRejectRequest("신청 정보 부족");

        given(userRepository.findByUserIdAndIsDeletedFalse(adminId))
                .willReturn(Optional.of(admin));
        given(issuerApplicationRepository
                .findUserIdByApplicationId(applicationId))
                .willReturn(Optional.of(applicantId));
        given(userRepository.findByUserIdForUpdate(applicantId))
                .willReturn(Optional.of(applicant));
        given(issuerApplicationRepository.findByIdForUpdate(applicationId))
                .willReturn(Optional.of(application));

        IssuerApplicationResponse response =
                issuerApplicationService.reject(
                        adminId,
                        applicationId,
                        request
                );

        assertThat(response.status())
                .isEqualTo(IssuerApplicationStatus.REJECTED);
        assertThat(response.reviewedBy()).isEqualTo(adminId);
        assertThat(response.reviewedAt()).isNotNull();
        assertThat(response.rejectionReason()).isEqualTo("신청 정보 부족");
        assertThat(applicant.getRole()).isEqualTo(UserRole.INVESTOR);
    }

    private User createInvestor() {
        return User.create(
                "investor@example.com",
                "encoded-password",
                "투자자",
                "01012345678"
        );
    }

    private User createAdmin() {
        User admin = User.create(
                "admin@example.com",
                "encoded-password",
                "관리자",
                "01087654321"
        );
        admin.updateUserByAdmin(null, null, null, UserRole.ADMIN);
        return admin;
    }
}

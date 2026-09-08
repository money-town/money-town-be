package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.user.dto.request.IssuerApplyRequest;
import com.moneykk.moneytown.user.dto.request.IssuerRejectRequest;
import com.moneykk.moneytown.user.dto.response.IssuerApplicationResponse;
import com.moneykk.moneytown.user.entity.IssuerApplication;
import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.entity.type.AccountStatus;
import com.moneykk.moneytown.user.entity.type.IssuerApplicationStatus;
import com.moneykk.moneytown.user.entity.type.UserRole;
import com.moneykk.moneytown.user.global.exception.IssuerApplicationErrorCode;
import com.moneykk.moneytown.user.global.exception.UserErrorCode;
import com.moneykk.moneytown.user.repository.IssuerApplicationRepository;
import com.moneykk.moneytown.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssuerApplicationService {

    private final IssuerApplicationRepository issuerApplicationRepository;
    private final UserRepository userRepository;

    @Transactional
    public IssuerApplicationResponse apply(
            UUID userId,
            IssuerApplyRequest request
    ) {
        User user = userRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() ->
                        new BusinessException(UserErrorCode.USER_NOT_FOUND));

        validateActiveAccount(user);

        if (user.getRole() == UserRole.ISSUER) {
            throw new BusinessException(
                    IssuerApplicationErrorCode.ALREADY_ISSUER);
        }

        boolean pendingExists = issuerApplicationRepository
                .existsByUserIdAndStatusAndIsDeletedFalse(
                        userId,
                        IssuerApplicationStatus.PENDING
                );

        if (pendingExists) {
            throw new BusinessException(
                    IssuerApplicationErrorCode.APPLICATION_ALREADY_PENDING);
        }

        IssuerApplication application = IssuerApplication.create(
                userId,
                request.applicationReason()
        );

        return IssuerApplicationResponse.from(
                issuerApplicationRepository.save(application));
    }

    @Transactional(readOnly = true)
    public IssuerApplicationResponse getCurrent(UUID userId) {
        IssuerApplication application = issuerApplicationRepository
                .findFirstByUserIdAndIsDeletedFalseOrderByAppliedAtDesc(userId)
                .orElseThrow(() -> new BusinessException(
                        IssuerApplicationErrorCode.APPLICATION_NOT_FOUND));

        return IssuerApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public PageResponse<IssuerApplicationResponse> getReviewList(
            UUID adminId,
            IssuerApplicationStatus status,
            Pageable pageable
    ) {
        validateAdmin(adminId);

        Page<IssuerApplication> applicationPage = status == null
                ? issuerApplicationRepository.findAllByIsDeletedFalse(pageable)
                : issuerApplicationRepository
                        .findAllByStatusAndIsDeletedFalse(status, pageable);

        return PageResponse.from(
                applicationPage,
                IssuerApplicationResponse::from
        );
    }

    @Transactional
    public IssuerApplicationResponse approve(
            UUID adminId,
            UUID applicationId
    ) {
        validateAdmin(adminId);

        User applicant = findApplicantForUpdate(applicationId);
        IssuerApplication application = findApplicationForUpdate(applicationId);

        validatePending(application);
        validateActiveAccount(applicant);

        application.approve(adminId);
        applicant.promoteToIssuer();

        return IssuerApplicationResponse.from(application);
    }

    @Transactional
    public IssuerApplicationResponse reject(
            UUID adminId,
            UUID applicationId,
            IssuerRejectRequest request
    ) {
        validateAdmin(adminId);

        findApplicantForUpdate(applicationId);
        IssuerApplication application = findApplicationForUpdate(applicationId);

        validatePending(application);
        application.reject(adminId, request.rejectionReason());

        return IssuerApplicationResponse.from(application);
    }

    private User findApplicantForUpdate(UUID applicationId) {
        UUID userId = issuerApplicationRepository
                .findUserIdByApplicationId(applicationId)
                .orElseThrow(() -> new BusinessException(
                        IssuerApplicationErrorCode.APPLICATION_NOT_FOUND));

        return userRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() ->
                        new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private IssuerApplication findApplicationForUpdate(UUID applicationId) {
        return issuerApplicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(
                        IssuerApplicationErrorCode.APPLICATION_NOT_FOUND));
    }

    private void validateAdmin(UUID adminId) {
        User admin = userRepository.findByUserIdAndIsDeletedFalse(adminId)
                .orElseThrow(() ->
                        new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new BusinessException(
                    IssuerApplicationErrorCode.ADMIN_REQUIRED);
        }
    }

    private void validatePending(IssuerApplication application) {
        if (!application.isPending()) {
            throw new BusinessException(
                    IssuerApplicationErrorCode.APPLICATION_NOT_PENDING);
        }
    }

    private void validateActiveAccount(User user) {
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(UserErrorCode.ACCOUNT_UNAVAILABLE);
        }
    }
}

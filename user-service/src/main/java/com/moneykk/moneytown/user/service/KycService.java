package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.user.dto.request.KycApplyRequest;
import com.moneykk.moneytown.user.dto.request.KycRejectRequest;
import com.moneykk.moneytown.user.dto.response.KycResponse;
import com.moneykk.moneytown.user.dto.response.UserInvestmentEligibilityResponse;
import com.moneykk.moneytown.user.entity.Kyc;
import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.entity.type.AccountStatus;
import com.moneykk.moneytown.user.entity.type.KycStatus;
import com.moneykk.moneytown.user.entity.type.KycVerificationStatus;
import com.moneykk.moneytown.user.global.exception.KycErrorCode;
import com.moneykk.moneytown.user.global.exception.UserErrorCode;
import com.moneykk.moneytown.user.repository.KycRepository;
import com.moneykk.moneytown.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycService {
    private final KycRepository kycRepository;
    private final UserRepository userRepository;


    // 내 kyc 현재 상태 조회
    @Transactional(readOnly = true)
    public KycResponse getCurrent(UUID userId){
        validateUserExists(userId);

        Kyc kyc = kycRepository.findFirstByUserIdAndIsDeletedFalseOrderByAttemptNoDesc(userId)
                .orElseThrow(() -> new BusinessException(KycErrorCode.KYC_NOT_FOUND));

        return KycResponse.from(kyc);
    }

    // 내 kyc 이력 조회
    @Transactional(readOnly = true)
    public List<KycResponse> getHistory(UUID userId){
        validateUserExists(userId);
        List<Kyc> kyc = kycRepository.findAllByUserIdAndIsDeletedFalseOrderByAttemptNoDesc(userId);

        return kyc.stream()
                .map(KycResponse::from)
                .toList();

    }




    // kyc 신청
    @Transactional
    public KycResponse apply(UUID userId, KycApplyRequest request) {
        User user = userRepository.findByUserIdForUpdate(userId).
                orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        validateAccountStatus(user);
        validateKycStatus(user);

        boolean pendingExists =
                kycRepository.existsByUserIdAndStatusAndIsDeletedFalse(userId,
                        KycVerificationStatus.PENDING);

        if (pendingExists) {throw new BusinessException(KycErrorCode.KYC_ALREADY_PENDING);}

        int nextAttemptNo = kycRepository.findMaxAttemptNoByUserId(userId) + 1;

        Kyc kyc = Kyc.create(
                userId,
                request.occupationType(),
                request.fundSource(),
                request.consentVersion(),
                request.domesticResident(),
                nextAttemptNo
        );

        Kyc savedKyc = kycRepository.save(kyc);
        user.submitKyc();

        return KycResponse.from(savedKyc);

    }


    // 관리자

    // 관리자 KYC 승인
    @Transactional
    public KycResponse approve(
            UUID adminId,
            UUID kycId,
            Instant expiresAt
    ) {
        // 신청 사용자 잠금 조회
        User user = findApplicantForUpdate(kycId);

        // 심사 대상 잠금 조회
        Kyc kyc = kycRepository.findByIdForUpdate(kycId)
                .orElseThrow(() ->
                        new BusinessException(KycErrorCode.KYC_NOT_FOUND));


        // 활성 계정 확인
        validateAccountStatus(user);

        // 신청 이력 승인
        kyc.approve(adminId, expiresAt);

        // 사용자 현재 상태 반영
        user.verifyKyc(expiresAt);

        return KycResponse.from(kyc);
    }

    // 관리자 KYC 거절
    @Transactional
    public KycResponse reject(
            UUID adminId,
            UUID kycId,
            KycRejectRequest request
    ) {
        User user = findApplicantForUpdate(kycId);
        // 승인과 동일한 잠금 조회
        Kyc kyc = kycRepository.findByIdForUpdate(kycId)
                .orElseThrow(() -> new BusinessException(KycErrorCode.KYC_NOT_FOUND));

        kyc.reject(adminId, request.rejectionReason());
        user.rejectKyc();

        return KycResponse.from(kyc);
    }



    // 관리자 KYC 심사 목록 조회
    @Transactional(readOnly = true)
    public PageResponse<KycResponse> getReviewList(
            KycVerificationStatus status,
            Pageable pageable
    ) {
        Page<Kyc> kycPage;

        if (status == null) {
            kycPage = kycRepository.findAllByIsDeletedFalse(pageable);
        } else {
            kycPage = kycRepository.findAllByStatusAndIsDeletedFalse(status, pageable);
        }

        return PageResponse.from(kycPage, KycResponse::from);
    }

    // KYC 만료 및 사용자 현재 상태 반영
    @Transactional
    public boolean expire(UUID kycId) {
        // 신청 사용자 먼저 잠금
        User user = findApplicantForUpdate(kycId);

        // 만료 대상 잠금
        Kyc kyc = kycRepository.findByIdForUpdate(kycId)
                .orElseThrow(() -> new BusinessException(KycErrorCode.KYC_NOT_FOUND));

        Instant now = Instant.now();

        // 승인 상태와 만료 시각 재확인
        boolean expired = kyc.expire(now);

        if (!expired) {return false;}

        // 삭제 이력을 포함한 최신 신청 회차 확인
        int latestAttemptNo = kycRepository.findMaxAttemptNoByUserId(user.getUserId());

        // 최신 신청인 경우에만 사용자 상태 반영
        if (kyc.getAttemptNo() == latestAttemptNo) {user.expireKyc(now);}

        return true;
    }

    // 청약용 사용자 상태 조회
    @Transactional(readOnly = true)
    public UserInvestmentEligibilityResponse getInvestmentEligibility(UUID userId) {
        User user = userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return UserInvestmentEligibilityResponse.from(user);
    }



    // 검증 로직

    // 신청 사용자 잠금 조회
    private User findApplicantForUpdate(UUID kycId) {
        UUID userId = kycRepository.findUserIdByKycId(kycId)
                .orElseThrow(() ->
                        new BusinessException(KycErrorCode.KYC_NOT_FOUND));

        return userRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() ->
                        new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    // 회원 활성 상태 확인
    private void validateAccountStatus(User user) {
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(UserErrorCode.ACCOUNT_UNAVAILABLE);
        }
    }

    // KYC 신청중인지 확인
    private void validateKycStatus(User user) {
        if (user.getKycStatus() == KycStatus.PENDING) {
            throw new BusinessException(
                    KycErrorCode.KYC_ALREADY_PENDING
            );
        }

        // 이미 유효한 KYC 인지 확인
        if (user.getKycStatus() == KycStatus.VERIFIED) {
            throw new BusinessException(
                    KycErrorCode.KYC_ALREADY_VERIFIED
            );
        }

    }

    private void validateUserExists(UUID userId) {
        if(!userRepository.existsByUserIdAndIsDeletedFalse(userId)){
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
    }


    public KycResponse getReview(UUID kycId) {
        Kyc kyc = kycRepository.findByIdAndIsDeletedFalse(kycId)
                .orElseThrow(() -> new BusinessException(KycErrorCode.KYC_NOT_FOUND));

        return KycResponse.from(kyc);
    }
}

package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.user.dto.request.AdminUpdateUserRequest;
import com.moneykk.moneytown.user.dto.request.UpdateMyInfoRequest;
import com.moneykk.moneytown.user.dto.response.UserInvestmentEligibilityResponse;
import com.moneykk.moneytown.user.dto.response.UserListResponse;
import com.moneykk.moneytown.user.dto.response.UserResponse;
import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.event.UserAccountEventWriter;
import com.moneykk.moneytown.user.global.exception.UserErrorCode;
import com.moneykk.moneytown.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserAccountEventWriter userAccountEventWriter;

    // 내부 투자 자격 조회
    @Transactional(readOnly = true)
    public UserInvestmentEligibilityResponse getInvestmentEligibility(
            UUID userId
    ){


        User user = userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return UserInvestmentEligibilityResponse.from(user, Instant.now());
    }


    // 사용자 목록 및 이름 검색
    @Transactional(readOnly = true)
    public PageResponse<UserListResponse> userList(String name,
                                                   Pageable pageable){
        Instant now = Instant.now();

        Page<User> users;
        if (name == null || name.isBlank()) {
            users = userRepository.findAllByIsDeletedFalse(pageable);
        } else {
            users = userRepository
                    .findAllByNameContainingAndIsDeletedFalse(name.trim(), pageable);
        }


        return PageResponse.from(
                users,
                user -> UserListResponse.from(user, now)
        );
    }

    // 회원 단일 조회
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId){


        User user = userRepository.findByUserIdAndIsDeletedFalse(userId).
                orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return UserResponse.from(user, Instant.now());
    }

    // 내 정보 조회
    @Transactional(readOnly = true)
    public UserResponse getUserMe(UUID userId){


        User user = userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return UserResponse.from(user, Instant.now());

    }


    // 회원 수정
    @Transactional
    public UserResponse updateUser(UUID userId, UpdateMyInfoRequest request){
        User user = userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        validateDuplicatePhoneForUpdate(request.phone(), userId);

        user.updateProfile(request.name(), request.phone());

        return UserResponse.from(user,Instant.now());

    }

    // 기존과 동일한 휴대전화 번호일 경우

    private void validateDuplicatePhoneForUpdate(String phone, UUID userId){
        if(phone != null && userRepository.existsByPhoneAndUserIdNot(phone, userId)){
            throw new BusinessException(
                    UserErrorCode.PHONE_ALREADY_EXISTS);
        }
    }


    // 회원 탈퇴
    @Transactional
    public UserResponse deleteUser(
            UUID userId,
            String correlationId
    ) {
        User user = userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        user.withdraw(userId);
        userAccountEventWriter.recordWithdrawn(
                userId,
                userId,
                correlationId
        );

        return UserResponse.from(user,Instant.now());
    }


    // 관리자 회원 수정
    @Transactional
    public UserResponse updateUserByAdmin(UUID userId, AdminUpdateUserRequest request) {
        User user = userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        validateDuplicatePhoneForUpdate(request.phone(), userId);

        user.updateUserByAdmin(request.name(),
                request.phone(),
                request.accountStatus(),
                request.role());

        return UserResponse.from(user,Instant.now());

    }

    // 관리자 회원 탈퇴
    @Transactional
    public void deleteUserByAdmin(
            UUID adminId,
            UUID userId,
            String correlationId
    ) {
        if(adminId.equals(userId)){
            throw new BusinessException(UserErrorCode.ADMIN_SELF_WITHDRAWAL_NOT_ALLOWED);
        }

        User user = userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        user.withdraw(adminId);
        userAccountEventWriter.recordWithdrawn(
                userId,
                adminId,
                correlationId
        );

    }




}   // UserService

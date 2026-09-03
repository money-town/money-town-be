package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.user.dto.request.SignupRequest;
import com.moneykk.moneytown.user.dto.request.UpdateMyInfoRequest;
import com.moneykk.moneytown.user.dto.response.SignupResponse;
import com.moneykk.moneytown.user.dto.response.UserListResponse;
import com.moneykk.moneytown.user.dto.response.UserResponse;
import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.global.exception.UserErrorCode;
import com.moneykk.moneytown.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    //CRUD


    // 회원 전체 조회
    public List<UserListResponse> userList(){

        return userRepository.findAllByIsDeletedFalse()
                .stream()
                .map(UserListResponse::from)
                .toList();

    }

    // 회원 단일 조회
    public UserResponse getUser(UUID userId){
        User user = userRepository.findByUserIdAndIsDeletedFalse(userId).
                orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return UserResponse.from(user);
    }

    // 내 정보 조회
    public UserResponse getUserMe(UUID userId){
        User user = userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return UserResponse.from(user);

    }


    // 회원 수정
    @Transactional
    public UserResponse updateUser(UUID userId, UpdateMyInfoRequest request){
        User user = userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        validateDuplicatePhoneForUpdate(request.phone(), userId);

        user.updateProfile(request.name(), request.phone());

        return UserResponse.from(user);

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
    public UserResponse deleteUser(UUID userId){
        User user = userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        user.withdraw(userId);

        return UserResponse.from(user);
    }

}   // UserService

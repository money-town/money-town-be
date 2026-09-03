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
    private final PasswordEncoder passwordEncoder;

    //CRUD

    // 생성
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String encodedPassword = passwordEncoder.encode(request.password());

        User user = User.create(request.email(),
                encodedPassword,
                request.name(),
                request.phone());

        User savedUser = userRepository.save(user);


        return SignupResponse.from(savedUser);
    }

    // 이메일 중복 방지
    private void validateDuplicateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(
                    UserErrorCode.EMAIL_ALREADY_EXISTS
            );
        }
    }

    // 회원 전체 조회
    public List<UserListResponse> userList(){



        return userRepository.findAll()
                .stream()
                .map(UserListResponse::from)
                .toList();

    }

    // 회원 단일 조회
    public UserResponse getUser(UUID userId){
        User user = userRepository.findByUserIdAndIsDeletedFalse(userId).
                orElseThrow();

        return UserResponse.from(user);
    }

    // 내 정보 조회
    public UserResponse getUserMe(UUID userId){
        User user = userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return UserResponse.from(user);

    }


    // 회원 수정
    public UserResponse getUserMe(UUID userId, UpdateMyInfoRequest request){
        User user = userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow();

        if(request.phone() != null
                && userRepository.existsByPhone(request.phone())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_PHONE);
        }

        user.updateProfile(
                request.name(),
                request.phone()
        );

        return UserResponse.from(user);

    }


    // 회원 탈퇴
    public UserResponse deleteUser(UUID userId){
        User user = userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow();

        user.softDelete(userId);

        return UserResponse.from(user);
    }






}   // UserService

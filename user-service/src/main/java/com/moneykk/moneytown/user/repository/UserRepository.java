package com.moneykk.moneytown.user.repository;

import com.moneykk.moneytown.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // 전체 조회
    List<User> findAllByIsDeletedFalse();

    // 단일 조회
    Optional<User> findByUserIdAndIsDeletedFalse(UUID userId);

    // 이메일 중복 방지
    boolean existsByEmail(String email);

    // 휴대폰 번호 중복 방지
    boolean existsByPhone(String phone);

    boolean existsByPhoneAndUserIdNot(String phone, UUID userId);

    // 이메일
    Optional<User> findByEmailAndIsDeletedFalse(String email);







}

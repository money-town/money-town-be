package com.moneykk.moneytown.user.repository;

import com.moneykk.moneytown.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUserIdAndIsDeletedFalse(UUID userId);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
}

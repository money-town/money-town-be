package com.moneykk.moneytown.user.entity;

import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import com.moneykk.moneytown.user.entity.type.AccountStatus;
import com.moneykk.moneytown.user.entity.type.KycStatus;
import com.moneykk.moneytown.user.entity.type.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "p_users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseUpdatableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 30)
    private AccountStatus accountStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 30)
    private KycStatus kycStatus;

    @Column(name = "kyc_expires_at")
    private Instant kycExpiresAt;

    public static User create(
            String email,
            String encodedPassword,
            String name,
            String phone
    ) {
        User user = new User();
        user.email = email;
        user.password = encodedPassword;
        user.name = name;
        user.phone = phone;
        user.role = UserRole.INVESTOR;
        user.accountStatus = AccountStatus.ACTIVE;
        user.kycStatus = KycStatus.NOT_SUBMITTED;
        return user;
    }

    public void updateProfile(String name, String phone){
        if(name != null) {
            this.name = name;
        }

        if(phone != null) {
            this.phone = phone;
        }
    }

    // 회원 삭제
    public void withdraw(UUID deletedBy){
        this.accountStatus = AccountStatus.WITHDRAWN;
        softDelete(deletedBy);
    }




}

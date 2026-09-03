package com.moneykk.moneytown.user.dto.response;

import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.entity.type.UserRole;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(UUID userId,
                            String email,
                            UserRole role) {


    public static LoginResponse from(User user){
        return new LoginResponse(
                user.getUserId(),
                user.getEmail(),
                user.getRole()
        );



    }
}

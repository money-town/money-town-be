package com.moneykk.moneytown.user.dto.request;

import com.moneykk.moneytown.user.entity.type.AccountStatus;
import com.moneykk.moneytown.user.entity.type.UserRole;

public record AdminUpdateUserRequest(
        String name,
        String phone,
        AccountStatus accountStatus,
        UserRole role
) {
}

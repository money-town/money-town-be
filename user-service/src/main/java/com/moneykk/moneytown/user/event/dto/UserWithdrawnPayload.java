package com.moneykk.moneytown.user.event.dto;

import java.util.UUID;

public record UserWithdrawnPayload(
        UUID withdrawnBy
) {
}

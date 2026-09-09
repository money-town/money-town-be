package com.moneykk.moneytown.user.event;

public final class UserAccountEventConstants {

    public static final String AGGREGATE_TYPE = "USER";
    public static final String TOPIC = "user.account-events.v1";
    public static final String USER_REGISTERED = "UserRegistered";
    public static final String USER_WITHDRAWN = "UserWithdrawn";

    private UserAccountEventConstants() {
    }
}

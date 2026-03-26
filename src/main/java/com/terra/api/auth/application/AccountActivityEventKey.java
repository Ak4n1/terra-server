package com.terra.api.auth.application;

public final class AccountActivityEventKey {

    public static final String AUTH_LOGIN_SUCCESS = "auth.login.success";
    public static final String GAME_ACCOUNT_CREATED = "game.account.created";
    public static final String GAME_ACCOUNT_PASSWORD_CHANGED = "game.account.password_changed";
    public static final String AUTH_TWO_FACTOR_ENABLED = "auth.two_factor.enabled";
    public static final String AUTH_TWO_FACTOR_DISABLED = "auth.two_factor.disabled";

    private AccountActivityEventKey() {
    }
}

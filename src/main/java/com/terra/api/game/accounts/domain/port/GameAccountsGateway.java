package com.terra.api.game.accounts.domain.port;

public interface GameAccountsGateway {
    boolean existsByLogin(String login);

    void createAccount(String login, String encodedPassword, String email);
}


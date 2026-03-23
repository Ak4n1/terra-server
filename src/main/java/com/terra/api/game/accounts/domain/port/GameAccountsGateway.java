package com.terra.api.game.accounts.domain.port;

import java.time.Instant;
import java.util.List;

public interface GameAccountsGateway {

    record GameAccountSummary(
            String login,
            String email,
            Instant createdAt,
            Instant lastActiveAt,
            int charactersCount
    ) {
    }

    boolean existsByLogin(String login);

    boolean existsByLoginAndEmail(String login, String email);

    void createAccount(String login, String encodedPassword, String email);

    int updatePassword(String login, String encodedPassword, String email);

    List<GameAccountSummary> findByEmailWithCharacters(String email);

    int countByEmail(String email);
}


package com.terra.api.game.accounts.infrastructure.persistence.jdbc;

import com.terra.api.game.accounts.domain.port.GameAccountsGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class GameAccountsJdbcRepository implements GameAccountsGateway {

    private final JdbcTemplate jdbcTemplate;
    private final GameAccountRowMapper rowMapper = new GameAccountRowMapper();

    public GameAccountsJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsByLogin(String login) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM accounts WHERE login = ?",
                Integer.class,
                login
        );
        return count != null && count > 0;
    }

    @Override
    public void createAccount(String login, String encodedPassword, String email) {
        long nowEpochMillis = System.currentTimeMillis();
        jdbcTemplate.update(
                """
                INSERT INTO accounts
                (login, password, email, created_time, lastactive, accessLevel, lastServer)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, 0, 1)
                """,
                login,
                encodedPassword,
                email,
                nowEpochMillis
        );
    }

    public List<Map<String, Object>> findByEmail(String email) {
        return jdbcTemplate.query(
                "SELECT login, email, created_time FROM accounts WHERE email = ? ORDER BY created_time DESC",
                rowMapper,
                email
        );
    }
}


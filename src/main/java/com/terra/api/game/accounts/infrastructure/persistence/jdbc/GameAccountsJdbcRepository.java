package com.terra.api.game.accounts.infrastructure.persistence.jdbc;

import com.terra.api.game.accounts.domain.port.GameAccountsGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Repository
public class GameAccountsJdbcRepository implements GameAccountsGateway {

    private final JdbcTemplate jdbcTemplate;

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
    public boolean existsByLoginAndEmail(String login, String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM accounts WHERE login = ? AND LOWER(COALESCE(email, '')) = ?",
                Integer.class,
                login,
                normalizedEmail
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

    @Override
    public int updatePassword(String login, String encodedPassword, String email) {
        long nowEpochMillis = System.currentTimeMillis();
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return jdbcTemplate.update(
                """
                UPDATE accounts
                SET password = ?, lastactive = ?
                WHERE login = ? AND LOWER(COALESCE(email, '')) = ?
                """,
                encodedPassword,
                nowEpochMillis,
                login,
                normalizedEmail
        );
    }

    @Override
    public List<GameAccountSummary> findByEmailWithCharacters(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return jdbcTemplate.query(
                """
                SELECT
                    a.login,
                    a.email,
                    a.created_time,
                    a.lastactive,
                    COUNT(c.charId) AS characters_count
                FROM accounts a
                LEFT JOIN characters c
                    ON c.account_name = a.login
                    AND c.deletetime = 0
                WHERE LOWER(COALESCE(a.email, '')) = ?
                GROUP BY a.login, a.email, a.created_time, a.lastactive
                ORDER BY a.lastactive DESC
                """,
                (rs, rowNum) -> new GameAccountSummary(
                        rs.getString("login"),
                        rs.getString("email"),
                        readInstant(rs.getTimestamp("created_time")),
                        readInstantFromEpoch(rs.getLong("lastactive")),
                        rs.getInt("characters_count")
                ),
                normalizedEmail
        );
    }

    @Override
    public int countByEmail(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM accounts WHERE LOWER(COALESCE(email, '')) = ?",
                Integer.class,
                normalizedEmail
        );
        return count == null ? 0 : Math.max(0, count);
    }

    private Instant readInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Instant readInstantFromEpoch(long epochMillis) {
        return epochMillis <= 0L ? null : Instant.ofEpochMilli(epochMillis);
    }
}



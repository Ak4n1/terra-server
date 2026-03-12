package com.terra.api.auth.repository;

import com.terra.api.auth.entity.AccountSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountSessionRepository extends JpaRepository<AccountSession, Long> {
    Optional<AccountSession> findByRefreshTokenHash(String refreshTokenHash);

    List<AccountSession> findByAccount_Id(Long accountId);
}

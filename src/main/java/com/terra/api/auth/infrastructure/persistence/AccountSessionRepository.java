package com.terra.api.auth.infrastructure.persistence;

import com.terra.api.auth.domain.model.AccountSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountSessionRepository extends JpaRepository<AccountSession, Long> {
    Optional<AccountSession> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountSession> findWithLockByRefreshTokenHash(String refreshTokenHash);

    List<AccountSession> findByAccount_Id(Long accountId);

    List<AccountSession> findByAccount_IdAndRevokedAtIsNullOrderByCreatedAtDesc(Long accountId);

    List<AccountSession> findByAccount_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(Long accountId, Instant now);

    Optional<AccountSession> findByIdAndAccount_Id(Long sessionId, Long accountId);
}

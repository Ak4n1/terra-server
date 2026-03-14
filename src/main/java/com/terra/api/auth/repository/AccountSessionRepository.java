package com.terra.api.auth.repository;

import com.terra.api.auth.entity.AccountSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface AccountSessionRepository extends JpaRepository<AccountSession, Long> {
    Optional<AccountSession> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountSession> findWithLockByRefreshTokenHash(String refreshTokenHash);

    List<AccountSession> findByAccount_Id(Long accountId);
}

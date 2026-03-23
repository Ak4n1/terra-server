package com.terra.api.game.accounts.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameAccountPasswordChangeCodeRepository extends JpaRepository<GameAccountPasswordChangeCodeEntity, Long> {
    Optional<GameAccountPasswordChangeCodeEntity> findByAccountId(Long accountId);
}


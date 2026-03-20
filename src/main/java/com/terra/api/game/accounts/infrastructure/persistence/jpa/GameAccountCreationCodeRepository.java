package com.terra.api.game.accounts.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameAccountCreationCodeRepository extends JpaRepository<GameAccountCreationCodeEntity, Long> {
    Optional<GameAccountCreationCodeEntity> findByAccountId(Long accountId);
}


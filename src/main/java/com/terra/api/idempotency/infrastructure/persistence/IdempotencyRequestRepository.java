package com.terra.api.idempotency.infrastructure.persistence;

import com.terra.api.idempotency.domain.model.IdempotencyRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IdempotencyRequest> findWithLockByScopeAndIdempotencyKey(String scope, String idempotencyKey);
}

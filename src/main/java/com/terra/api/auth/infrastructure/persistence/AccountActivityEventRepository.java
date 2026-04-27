package com.terra.api.auth.infrastructure.persistence;

import com.terra.api.auth.domain.model.AccountActivityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface AccountActivityEventRepository extends JpaRepository<AccountActivityEvent, Long> {

    Page<AccountActivityEvent> findByAccount_Id(Long accountId, Pageable pageable);

    Page<AccountActivityEvent> findByAccount_IdAndOccurredAtGreaterThanEqual(
            Long accountId,
            Instant occurredFrom,
            Pageable pageable
    );

    Page<AccountActivityEvent> findByAccount_IdAndOccurredAtLessThan(
            Long accountId,
            Instant occurredToExclusive,
            Pageable pageable
    );

    Page<AccountActivityEvent> findByAccount_IdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            Long accountId,
            Instant occurredFrom,
            Instant occurredToExclusive,
            Pageable pageable
    );
}

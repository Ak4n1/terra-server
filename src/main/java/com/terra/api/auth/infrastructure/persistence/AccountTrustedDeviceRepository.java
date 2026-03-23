package com.terra.api.auth.infrastructure.persistence;

import com.terra.api.auth.domain.model.AccountTrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountTrustedDeviceRepository extends JpaRepository<AccountTrustedDevice, Long> {

    List<AccountTrustedDevice> findByAccount_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDescCreatedAtDesc(Long accountId, Instant now);

    Optional<AccountTrustedDevice> findByIdAndAccount_Id(Long id, Long accountId);

    Optional<AccountTrustedDevice> findByAccount_IdAndDeviceKeyHashAndRevokedAtIsNullAndExpiresAtAfter(Long accountId, String deviceKeyHash, Instant now);

    Optional<AccountTrustedDevice> findByAccount_IdAndDeviceKeyHash(Long accountId, String deviceKeyHash);

    List<AccountTrustedDevice> findByAccount_IdAndRevokedAtIsNull(Long accountId);
}

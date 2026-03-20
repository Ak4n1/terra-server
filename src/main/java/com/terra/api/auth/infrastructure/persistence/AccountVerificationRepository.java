package com.terra.api.auth.infrastructure.persistence;

import com.terra.api.auth.domain.model.AccountVerification;
import com.terra.api.auth.domain.model.AccountVerificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountVerificationRepository extends JpaRepository<AccountVerification, Long> {
    Optional<AccountVerification> findByAccount_IdAndType(Long accountId, AccountVerificationType type);

    Optional<AccountVerification> findByTokenHashAndType(String tokenHash, AccountVerificationType type);
}

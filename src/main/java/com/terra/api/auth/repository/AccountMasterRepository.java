package com.terra.api.auth.repository;

import com.terra.api.auth.entity.AccountMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountMasterRepository extends JpaRepository<AccountMaster, Long> {
    boolean existsByEmailIgnoreCase(String email);

    Optional<AccountMaster> findByEmailIgnoreCase(String email);
}

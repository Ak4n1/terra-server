package com.terra.api.auth.repository;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountMasterRepository extends JpaRepository<AccountMaster, Long> {
    boolean existsByEmailIgnoreCase(String email);

    Optional<AccountMaster> findByEmailIgnoreCase(String email);

    List<AccountMaster> findDistinctByRoles_Name(RoleName roleName);

    @Query("select account from AccountMaster account where account.enabled = true")
    List<AccountMaster> findAllEnabledAccounts();

    List<AccountMaster> findByEnabledTrueAndEmailVerifiedTrue();

    List<AccountMaster> findByEnabledTrueAndEmailVerifiedFalse();
}

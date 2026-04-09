package com.terra.api.auth.infrastructure.persistence;

import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.domain.model.AuthProvider;
import com.terra.api.auth.domain.model.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountMasterRepository extends JpaRepository<AccountMaster, Long> {
    boolean existsByEmailIgnoreCase(String email);

    Optional<AccountMaster> findByEmailIgnoreCase(String email);
    Optional<AccountMaster> findByPublicId(String publicId);
    Optional<AccountMaster> findByAuthProviderAndProviderSubject(AuthProvider authProvider, String providerSubject);

    List<AccountMaster> findDistinctByRoles_Name(RoleName roleName);

    @Query("select account from AccountMaster account where account.enabled = true")
    List<AccountMaster> findAllEnabledAccounts();

    List<AccountMaster> findByEnabledTrueAndEmailVerifiedTrue();

    List<AccountMaster> findByEnabledTrueAndEmailVerifiedFalse();

    @Query(
            value = """
                    SELECT COUNT(1)
                    FROM account_master
                    WHERE username IS NOT NULL
                      AND BINARY username = BINARY :username
                      AND account_id <> :accountId
                    """,
            nativeQuery = true
    )
    long countByUsernameExactAndAccountIdNot(@Param("username") String username, @Param("accountId") Long accountId);
}

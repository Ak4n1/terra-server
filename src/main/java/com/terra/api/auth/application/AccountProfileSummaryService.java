package com.terra.api.auth.application;

import com.terra.api.auth.api.dto.AccountProfileSummaryResponse;
import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.game.accounts.domain.port.GameAccountsGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AccountProfileSummaryService {

    private final AuthService authService;
    private final GameAccountsGateway gameAccountsGateway;

    public AccountProfileSummaryService(AuthService authService,
                                        GameAccountsGateway gameAccountsGateway) {
        this.authService = authService;
        this.gameAccountsGateway = gameAccountsGateway;
    }

    @Transactional(readOnly = true)
    public AccountProfileSummaryResponse getSummary(String authenticatedEmail,
                                                    boolean includeTotalAccounts,
                                                    boolean includeLastLogin,
                                                    boolean includeCreatedAt) {
        AccountMaster accountMaster = authService.getCurrentUserAccount(authenticatedEmail);

        Integer totalAccounts = null;
        Instant lastLoginAt = null;
        Instant createdAt = null;

        if (includeTotalAccounts) {
            totalAccounts = gameAccountsGateway.countByEmail(accountMaster.getEmail());
        }

        if (includeLastLogin) {
            lastLoginAt = accountMaster.getLastLoginAt();
        }

        if (includeCreatedAt) {
            createdAt = accountMaster.getCreatedAt();
        }

        return new AccountProfileSummaryResponse(totalAccounts, lastLoginAt, createdAt);
    }
}

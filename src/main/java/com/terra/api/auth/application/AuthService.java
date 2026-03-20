package com.terra.api.auth.application;

import com.terra.api.auth.api.dto.LoginRequest;
import com.terra.api.auth.api.dto.RegisterRequest;
import com.terra.api.auth.api.dto.UpdatePreferredLanguageRequest;
import com.terra.api.auth.api.dto.UserResponse;
import com.terra.api.auth.domain.model.AccountMaster;

public interface AuthService {
    UserResponse register(RegisterRequest request);

    AccountMaster authenticate(LoginRequest request);

    UserResponse getCurrentUser(String email);

    AccountMaster getCurrentUserAccount(String email);

    AccountMaster getCurrentUserAccount(Long accountId);

    void revokeAllSessions(String email);

    UserResponse updatePreferredLanguage(String email, UpdatePreferredLanguageRequest request);
}

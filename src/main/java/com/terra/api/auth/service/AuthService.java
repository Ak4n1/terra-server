package com.terra.api.auth.service;

import com.terra.api.auth.dto.LoginRequest;
import com.terra.api.auth.dto.RegisterRequest;
import com.terra.api.auth.dto.UserResponse;
import com.terra.api.auth.entity.AccountMaster;

public interface AuthService {
    UserResponse register(RegisterRequest request);

    AccountMaster authenticate(LoginRequest request);

    UserResponse getCurrentUser(String email);

    AccountMaster getCurrentUserAccount(String email);

    AccountMaster getCurrentUserAccount(Long accountId);

    void revokeAllSessions(String email);
}

package com.terra.api.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfirmTwoFactorRecoveryRequest {

    @NotBlank(message = "validation.token.required")
    private String token;

    @NotBlank(message = "validation.password.required")
    private String currentPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }
}

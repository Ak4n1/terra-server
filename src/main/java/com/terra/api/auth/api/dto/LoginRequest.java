package com.terra.api.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @Email(message = "validation.email.invalid")
    @NotBlank(message = "validation.email.required")
    private String email;

    @NotBlank(message = "validation.password.required")
    @Size(min = 1, max = 100, message = "validation.password.login_length")
    private String password;

    @Size(min = 0, max = 8, message = "validation.token.required")
    private String twoFactorCode;

    private boolean trustDevice;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTwoFactorCode() {
        return twoFactorCode;
    }

    public void setTwoFactorCode(String twoFactorCode) {
        this.twoFactorCode = twoFactorCode;
    }

    public boolean isTrustDevice() {
        return trustDevice;
    }

    public void setTrustDevice(boolean trustDevice) {
        this.trustDevice = trustDevice;
    }
}

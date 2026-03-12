package com.terra.api.auth.dto;

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
}

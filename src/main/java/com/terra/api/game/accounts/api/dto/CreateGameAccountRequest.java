package com.terra.api.game.accounts.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateGameAccountRequest {

    @NotBlank(message = "game.validation.account_name.required")
    @Size(min = 4, max = 14, message = "game.validation.account_name.length")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "game.validation.account_name.format")
    private String accountName;

    @NotBlank(message = "game.validation.password.required")
    @Size(min = 8, max = 16, message = "game.validation.password.length")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,16}$",
            message = "game.validation.password.strength"
    )
    private String password;

    @NotBlank(message = "game.validation.verification_token.required")
    private String verificationToken;

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }
}

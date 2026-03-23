package com.terra.api.game.accounts.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ChangeGamePasswordRequest {

    @NotBlank(message = "game.change_password.validation.account_name.required")
    @Size(min = 4, max = 14, message = "game.change_password.validation.account_name.length")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "game.change_password.validation.account_name.format")
    private String accountName;

    @NotBlank(message = "game.change_password.validation.new_password.required")
    @Size(min = 8, max = 16, message = "game.change_password.validation.new_password.length")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,16}$",
            message = "game.change_password.validation.new_password.strength"
    )
    private String newPassword;

    @NotBlank(message = "game.change_password.validation.confirm_password.required")
    private String confirmPassword;

    @NotBlank(message = "game.change_password.validation.verification_token.required")
    private String verificationToken;

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }
}


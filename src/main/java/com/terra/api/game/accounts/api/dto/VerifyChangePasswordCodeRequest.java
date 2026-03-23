package com.terra.api.game.accounts.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class VerifyChangePasswordCodeRequest {

    @NotBlank(message = "game.change_password.validation.account_name.required")
    @Size(min = 4, max = 14, message = "game.change_password.validation.account_name.length")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "game.change_password.validation.account_name.format")
    private String accountName;

    @NotBlank(message = "game.change_password.validation.code.required")
    @Size(min = 6, max = 6, message = "game.change_password.validation.code.length")
    @Pattern(regexp = "^[0-9]{6}$", message = "game.change_password.validation.code.format")
    private String code;

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}


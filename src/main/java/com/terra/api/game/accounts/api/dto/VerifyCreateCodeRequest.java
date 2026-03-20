package com.terra.api.game.accounts.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class VerifyCreateCodeRequest {

    @NotBlank(message = "game.validation.create_code.required")
    @Size(min = 6, max = 6, message = "game.validation.create_code.length")
    @Pattern(regexp = "^[0-9]{6}$", message = "game.validation.create_code.format")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}

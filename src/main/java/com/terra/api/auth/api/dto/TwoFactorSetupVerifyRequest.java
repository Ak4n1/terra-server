package com.terra.api.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public class TwoFactorSetupVerifyRequest {

    @NotBlank(message = "validation.token.required")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}

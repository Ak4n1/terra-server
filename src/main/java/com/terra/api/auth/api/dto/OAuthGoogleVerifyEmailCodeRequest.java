package com.terra.api.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class OAuthGoogleVerifyEmailCodeRequest {

    @NotBlank(message = "validation.token.required")
    @Size(max = 64, message = "validation.token.required")
    private String challengeId;

    @NotBlank(message = "validation.code.required")
    @Size(min = 6, max = 6, message = "validation.code.length")
    @Pattern(regexp = "^[0-9]{6}$", message = "validation.code.format")
    private String code;

    private boolean trustDevice;

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isTrustDevice() {
        return trustDevice;
    }

    public void setTrustDevice(boolean trustDevice) {
        this.trustDevice = trustDevice;
    }
}

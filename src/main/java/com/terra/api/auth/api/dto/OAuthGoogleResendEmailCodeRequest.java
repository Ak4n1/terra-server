package com.terra.api.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OAuthGoogleResendEmailCodeRequest {

    @NotBlank(message = "validation.token.required")
    @Size(max = 64, message = "validation.token.required")
    private String challengeId;

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }
}

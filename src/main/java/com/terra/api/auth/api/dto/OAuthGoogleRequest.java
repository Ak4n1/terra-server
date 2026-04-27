package com.terra.api.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OAuthGoogleRequest {

    @NotBlank(message = "validation.token.required")
    @Size(max = 8192, message = "validation.token.required")
    private String idToken;

    private boolean trustDevice;

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public boolean isTrustDevice() {
        return trustDevice;
    }

    public void setTrustDevice(boolean trustDevice) {
        this.trustDevice = trustDevice;
    }
}

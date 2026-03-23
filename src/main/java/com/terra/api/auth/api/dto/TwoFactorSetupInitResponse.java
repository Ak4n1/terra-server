package com.terra.api.auth.api.dto;

public record TwoFactorSetupInitResponse(
        String secret,
        String otpAuthUrl,
        String qrImageDataUrl
) {
}

package com.terra.api.auth.application;

import com.terra.api.auth.domain.model.AccountMaster;

public record TwoFactorSetupVerificationResult(
        AccountMaster account,
        String trustedDeviceKeyToSet
) {
}

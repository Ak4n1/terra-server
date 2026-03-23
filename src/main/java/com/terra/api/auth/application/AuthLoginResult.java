package com.terra.api.auth.application;

import com.terra.api.auth.domain.model.AccountMaster;

public record AuthLoginResult(
        AccountMaster account,
        String trustedDeviceKeyToSet
) {
}

package com.terra.api.game.accounts.domain.exception;

import com.terra.api.common.domain.exception.BadRequestException;

public class GameAccountVerificationRequiredException extends BadRequestException {
    public GameAccountVerificationRequiredException() {
        super("game.create_code_verification_required");
    }
}


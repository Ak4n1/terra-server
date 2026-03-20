package com.terra.api.game.accounts.domain.exception;

import com.terra.api.common.domain.exception.BadRequestException;

public class GameAccountCodeExpiredException extends BadRequestException {
    public GameAccountCodeExpiredException() {
        super("game.create_code_expired");
    }
}


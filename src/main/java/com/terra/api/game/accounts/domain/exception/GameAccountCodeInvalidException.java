package com.terra.api.game.accounts.domain.exception;

import com.terra.api.common.domain.exception.BadRequestException;

public class GameAccountCodeInvalidException extends BadRequestException {
    public GameAccountCodeInvalidException() {
        super("game.create_code_invalid");
    }
}


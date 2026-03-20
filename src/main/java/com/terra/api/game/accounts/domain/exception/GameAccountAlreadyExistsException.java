package com.terra.api.game.accounts.domain.exception;

import com.terra.api.common.domain.exception.ResourceConflictException;

public class GameAccountAlreadyExistsException extends ResourceConflictException {
    public GameAccountAlreadyExistsException() {
        super("game.account_already_exists");
    }
}


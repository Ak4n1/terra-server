package com.terra.api.game.accounts.application;

import com.terra.api.common.domain.exception.BadRequestException;
import org.springframework.stereotype.Service;

@Service
public class GameAccountValidationService {

    public void validateAccountName(String accountName) {
        String value = accountName == null ? "" : accountName.trim();
        if (value.length() < 4 || value.length() > 14) {
            throw new BadRequestException("game.validation.account_name.length");
        }
        if (!value.matches("^[A-Za-z0-9_]+$")) {
            throw new BadRequestException("game.validation.account_name.format");
        }
    }

    public void validatePassword(String password) {
        String value = password == null ? "" : password;
        if (value.length() < 8 || value.length() > 16) {
            throw new BadRequestException("game.validation.password.length");
        }
        if (!value.matches("^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,16}$")) {
            throw new BadRequestException("game.validation.password.strength");
        }
    }
}

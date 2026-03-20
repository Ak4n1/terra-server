package com.terra.api.game.accounts.application;

import com.terra.api.game.shared.infrastructure.encoding.L2ClientPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class GameAccountPasswordService {

    public String encodeForGameClient(String rawPassword) {
        return L2ClientPasswordEncoder.encode(rawPassword);
    }
}


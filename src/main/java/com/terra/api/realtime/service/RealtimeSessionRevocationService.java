package com.terra.api.realtime.service;

import com.terra.api.realtime.websocket.RealtimeWebSocketHandler;
import org.springframework.stereotype.Service;

@Service
public class RealtimeSessionRevocationService {

    private final RealtimeWebSocketHandler realtimeWebSocketHandler;

    public RealtimeSessionRevocationService(RealtimeWebSocketHandler realtimeWebSocketHandler) {
        this.realtimeWebSocketHandler = realtimeWebSocketHandler;
    }

    public void revokeAccountSessions(Long accountId, String reason) {
        realtimeWebSocketHandler.closeAccountSessions(accountId, reason);
    }

    public void revokeAccountSession(Long accountSessionId, Long accountId, String reason) {
        realtimeWebSocketHandler.closeAccountSession(accountSessionId, accountId, reason);
    }
}

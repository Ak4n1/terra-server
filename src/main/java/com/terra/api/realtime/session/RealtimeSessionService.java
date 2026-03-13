package com.terra.api.realtime.session;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.AccountSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

@Service
public class RealtimeSessionService {

    private final RealtimeSessionRepository realtimeSessionRepository;
    private final String nodeId;

    public RealtimeSessionService(RealtimeSessionRepository realtimeSessionRepository,
                                  @Value("${app.realtime.node-id:${spring.application.name}:local}") String nodeId) {
        this.realtimeSessionRepository = realtimeSessionRepository;
        this.nodeId = nodeId;
    }

    @Transactional
    public RealtimeSession openSession(AccountMaster accountMaster,
                                       AccountSession accountSession,
                                       String origin,
                                       String clientIp,
                                       String userAgent) {
        RealtimeSession session = new RealtimeSession();
        session.setAccount(accountMaster);
        session.setAccountSession(accountSession);
        session.setNodeId(nodeId);
        session.setOrigin(origin);
        session.setClientIp(clientIp);
        session.setUserAgentHash(hashUserAgent(userAgent));
        return realtimeSessionRepository.save(session);
    }

    @Transactional
    public void touchSession(String realtimeSessionId) {
        realtimeSessionRepository.findByRealtimeSessionId(realtimeSessionId)
                .ifPresent(session -> session.setLastSeenAt(Instant.now()));
    }

    @Transactional
    public void closeSession(String realtimeSessionId, RealtimeSessionStatus status, String reason) {
        realtimeSessionRepository.findByRealtimeSessionId(realtimeSessionId)
                .ifPresent(session -> {
                    RealtimeSessionStatus finalStatus = session.getStatus() == RealtimeSessionStatus.REVOKED
                            ? RealtimeSessionStatus.REVOKED
                            : status;
                    session.close(finalStatus, reason);
                });
    }

    @Transactional
    public List<RealtimeSession> revokeActiveSessions(Long accountId, String reason) {
        List<RealtimeSession> sessions = realtimeSessionRepository.findByAccount_IdAndStatus(accountId, RealtimeSessionStatus.OPEN);
        for (RealtimeSession session : sessions) {
            session.close(RealtimeSessionStatus.REVOKED, reason);
        }
        return sessions;
    }

    @Transactional
    public List<RealtimeSession> revokeActiveSessionsByAccountSession(Long accountSessionId, String reason) {
        List<RealtimeSession> sessions = realtimeSessionRepository.findByAccountSession_IdAndStatus(
                accountSessionId,
                RealtimeSessionStatus.OPEN
        );
        for (RealtimeSession session : sessions) {
            session.close(RealtimeSessionStatus.REVOKED, reason);
        }
        return sessions;
    }

    private String hashUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(userAgent.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}

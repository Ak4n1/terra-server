package com.terra.api.realtime.session;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.AccountSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_realtime_session")
public class RealtimeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "realtime_session_pk")
    private Long id;

    @Column(name = "realtime_session_id", nullable = false, unique = true, length = 64, updatable = false)
    private String realtimeSessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountMaster account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_session_id", nullable = false)
    private AccountSession accountSession;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RealtimeSessionStatus status;

    @Column(name = "origin", nullable = false, length = 255)
    private String origin;

    @Column(name = "client_ip", nullable = false, length = 64)
    private String clientIp;

    @Column(name = "user_agent_hash", length = 128)
    private String userAgentHash;

    @Column(name = "connected_at", nullable = false, updatable = false)
    private Instant connectedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "close_reason", length = 128)
    private String closeReason;

    @PrePersist
    void onCreate() {
        this.realtimeSessionId = "rts_" + UUID.randomUUID();
        this.connectedAt = Instant.now();
        this.lastSeenAt = connectedAt;
        this.status = RealtimeSessionStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public String getRealtimeSessionId() {
        return realtimeSessionId;
    }

    public AccountMaster getAccount() {
        return account;
    }

    public void setAccount(AccountMaster account) {
        this.account = account;
    }

    public AccountSession getAccountSession() {
        return accountSession;
    }

    public void setAccountSession(AccountSession accountSession) {
        this.accountSession = accountSession;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public RealtimeSessionStatus getStatus() {
        return status;
    }

    public void setStatus(RealtimeSessionStatus status) {
        this.status = status;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgentHash() {
        return userAgentHash;
    }

    public void setUserAgentHash(String userAgentHash) {
        this.userAgentHash = userAgentHash;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public void close(RealtimeSessionStatus status, String closeReason) {
        this.status = status;
        this.closedAt = Instant.now();
        this.closeReason = closeReason;
        this.lastSeenAt = this.closedAt;
    }
}

package com.terra.api.notifications.domain;

import com.terra.api.auth.entity.AccountMaster;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_notification")
public class AccountNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_pk")
    private Long id;

    @Column(name = "notification_id", nullable = false, unique = true, length = 64, updatable = false)
    private String notificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountMaster account;

    @Column(name = "type", nullable = false, length = 80)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private NotificationSeverity severity;

    @Column(name = "title_key", nullable = false, length = 120)
    private String titleKey;

    @Column(name = "body_key", nullable = false, length = 120)
    private String bodyKey;

    @Column(name = "params_json", nullable = false, columnDefinition = "TEXT")
    private String paramsJson = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status = NotificationStatus.UNREAD;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 32)
    private NotificationActionType actionType;

    @Column(name = "action_label_key", length = 120)
    private String actionLabelKey;

    @Column(name = "action_payload_json", columnDefinition = "TEXT")
    private String actionPayloadJson;

    @Column(name = "dedupe_key", unique = true, length = 120)
    private String dedupeKey;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (notificationId == null || notificationId.isBlank()) {
            notificationId = "ntf_" + UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = now;
        }
        if (status == null) {
            status = NotificationStatus.UNREAD;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markRead() {
        if (status == NotificationStatus.READ) {
            return;
        }
        status = NotificationStatus.READ;
        readAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getNotificationId() { return notificationId; }
    public AccountMaster getAccount() { return account; }
    public void setAccount(AccountMaster account) { this.account = account; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public NotificationCategory getCategory() { return category; }
    public void setCategory(NotificationCategory category) { this.category = category; }
    public NotificationSeverity getSeverity() { return severity; }
    public void setSeverity(NotificationSeverity severity) { this.severity = severity; }
    public String getTitleKey() { return titleKey; }
    public void setTitleKey(String titleKey) { this.titleKey = titleKey; }
    public String getBodyKey() { return bodyKey; }
    public void setBodyKey(String bodyKey) { this.bodyKey = bodyKey; }
    public String getParamsJson() { return paramsJson; }
    public void setParamsJson(String paramsJson) { this.paramsJson = paramsJson; }
    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }
    public NotificationActionType getActionType() { return actionType; }
    public void setActionType(NotificationActionType actionType) { this.actionType = actionType; }
    public String getActionLabelKey() { return actionLabelKey; }
    public void setActionLabelKey(String actionLabelKey) { this.actionLabelKey = actionLabelKey; }
    public String getActionPayloadJson() { return actionPayloadJson; }
    public void setActionPayloadJson(String actionPayloadJson) { this.actionPayloadJson = actionPayloadJson; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

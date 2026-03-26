package com.terra.api.auth.domain.model;

import com.terra.api.common.domain.i18n.SupportedLanguage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "account_master")
public class AccountMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "username", length = 24)
    private String username;

    @Column(name = "username_updated_at")
    private Instant usernameUpdatedAt;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "avatar_type", length = 16)
    private AccountAvatarType avatarType = AccountAvatarType.DEFAULT;

    @Column(name = "avatar_preset_path", length = 255)
    private String avatarPresetPath;

    @Column(name = "avatar_custom_file_name", length = 255)
    private String avatarCustomFileName;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    @Column(name = "two_factor_secret", length = 255)
    private String twoFactorSecret;

    @Column(name = "two_factor_enabled_at")
    private Instant twoFactorEnabledAt;

    @Column(name = "two_factor_recovery_requested_at")
    private Instant twoFactorRecoveryRequestedAt;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 8, columnDefinition = "VARCHAR(8) DEFAULT 'US'")
    private SupportedLanguage preferredLanguage = SupportedLanguage.defaultLanguage();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "accounts_master_role",
            joinColumns = @JoinColumn(name = "account_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        tokenVersion = 0L;
        if (publicId == null || publicId.isBlank()) {
            publicId = UUID.randomUUID().toString();
        }
        if (preferredLanguage == null) {
            preferredLanguage = SupportedLanguage.defaultLanguage();
        }
        if (avatarType == null) {
            avatarType = AccountAvatarType.DEFAULT;
        }
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Instant getUsernameUpdatedAt() {
        return usernameUpdatedAt;
    }

    public void setUsernameUpdatedAt(Instant usernameUpdatedAt) {
        this.usernameUpdatedAt = usernameUpdatedAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public void setTwoFactorEnabled(boolean twoFactorEnabled) {
        this.twoFactorEnabled = twoFactorEnabled;
    }

    public String getTwoFactorSecret() {
        return twoFactorSecret;
    }

    public void setTwoFactorSecret(String twoFactorSecret) {
        this.twoFactorSecret = twoFactorSecret;
    }

    public Instant getTwoFactorEnabledAt() {
        return twoFactorEnabledAt;
    }

    public void setTwoFactorEnabledAt(Instant twoFactorEnabledAt) {
        this.twoFactorEnabledAt = twoFactorEnabledAt;
    }

    public Instant getTwoFactorRecoveryRequestedAt() {
        return twoFactorRecoveryRequestedAt;
    }

    public void setTwoFactorRecoveryRequestedAt(Instant twoFactorRecoveryRequestedAt) {
        this.twoFactorRecoveryRequestedAt = twoFactorRecoveryRequestedAt;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = new HashSet<>(roles);
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(long tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public SupportedLanguage getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(SupportedLanguage preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public AccountAvatarType getAvatarType() {
        return avatarType;
    }

    public void setAvatarType(AccountAvatarType avatarType) {
        this.avatarType = avatarType;
    }

    public String getAvatarPresetPath() {
        return avatarPresetPath;
    }

    public void setAvatarPresetPath(String avatarPresetPath) {
        this.avatarPresetPath = avatarPresetPath;
    }

    public String getAvatarCustomFileName() {
        return avatarCustomFileName;
    }

    public void setAvatarCustomFileName(String avatarCustomFileName) {
        this.avatarCustomFileName = avatarCustomFileName;
    }

    public Instant getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public void setAvatarUpdatedAt(Instant avatarUpdatedAt) {
        this.avatarUpdatedAt = avatarUpdatedAt;
    }
}

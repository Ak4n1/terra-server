package com.terra.api.auth.application;

import com.terra.api.auth.api.dto.LoginRequest;
import com.terra.api.auth.api.dto.RegisterRequest;
import com.terra.api.auth.api.dto.UpdateProfileRequest;
import com.terra.api.auth.api.dto.UpdatePreferredLanguageRequest;
import com.terra.api.auth.api.dto.UserResponse;
import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.domain.model.AccountAvatarType;
import com.terra.api.auth.domain.model.AccountTrustedDevice;
import com.terra.api.auth.domain.model.Role;
import com.terra.api.auth.domain.model.RoleName;
import com.terra.api.auth.infrastructure.config.ProfileSettingsProperties;
import com.terra.api.auth.infrastructure.persistence.AccountMasterRepository;
import com.terra.api.auth.infrastructure.persistence.AccountTrustedDeviceRepository;
import com.terra.api.common.domain.exception.BadRequestException;
import com.terra.api.common.domain.i18n.SupportedLanguage;
import com.terra.api.common.infrastructure.i18n.CurrentLanguageResolver;
import com.terra.api.common.domain.exception.TooManyRequestsException;
import com.terra.api.notifications.application.NotificationCommandService;
import com.terra.api.auth.infrastructure.persistence.RoleRepository;
import com.terra.api.common.domain.exception.ForbiddenException;
import com.terra.api.common.domain.exception.ResourceConflictException;
import com.terra.api.common.domain.exception.ResourceNotFoundException;
import com.terra.api.realtime.application.RealtimeSessionRevocationService;
import com.terra.api.security.application.AccountSessionService;
import com.terra.api.security.application.ClientIpResolver;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.time.SystemTimeProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.Set;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Base64;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");
    private static final int USERNAME_MIN_LENGTH = 3;
    private static final int USERNAME_MAX_LENGTH = 24;

    private final AccountMasterRepository accountMasterRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AccountSessionService accountSessionService;
    private final EmailVerificationService emailVerificationService;
    private final RealtimeSessionRevocationService realtimeSessionRevocationService;
    private final NotificationCommandService notificationCommandService;
    private final CurrentLanguageResolver currentLanguageResolver;
    private final ProfileSettingsProperties profileSettingsProperties;
    private final AccountTrustedDeviceRepository accountTrustedDeviceRepository;
    private final ClientIpResolver clientIpResolver;
    private final CodeGenerator totpCodeGenerator = new DefaultCodeGenerator();
    private final CodeVerifier totpCodeVerifier = new DefaultCodeVerifier(totpCodeGenerator, new SystemTimeProvider());
    private final SecureRandom secureRandom = new SecureRandom();
    private static final long TRUSTED_DEVICE_EXPIRATION_DAYS = 30L;

    public AuthServiceImpl(
            AccountMasterRepository accountMasterRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            AccountSessionService accountSessionService,
            EmailVerificationService emailVerificationService,
            RealtimeSessionRevocationService realtimeSessionRevocationService,
            NotificationCommandService notificationCommandService,
            CurrentLanguageResolver currentLanguageResolver,
            ProfileSettingsProperties profileSettingsProperties,
            AccountTrustedDeviceRepository accountTrustedDeviceRepository,
            ClientIpResolver clientIpResolver) {
        this.accountMasterRepository = accountMasterRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.accountSessionService = accountSessionService;
        this.emailVerificationService = emailVerificationService;
        this.realtimeSessionRevocationService = realtimeSessionRevocationService;
        this.notificationCommandService = notificationCommandService;
        this.currentLanguageResolver = currentLanguageResolver;
        this.profileSettingsProperties = profileSettingsProperties;
        this.accountTrustedDeviceRepository = accountTrustedDeviceRepository;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (accountMasterRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResourceConflictException("auth.email_already_registered");
        }

        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new ResourceNotFoundException("auth.default_user_role_not_found"));

        AccountMaster accountMaster = new AccountMaster();
        accountMaster.setEmail(normalizedEmail);
        accountMaster.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        accountMaster.setEmailVerified(false);
        accountMaster.setRoles(Set.of(userRole));
        accountMaster.setPreferredLanguage(currentLanguageResolver.resolve());

        AccountMaster savedAccountMaster = accountMasterRepository.save(accountMaster);
        emailVerificationService.createOrRefreshEmailVerification(savedAccountMaster);
        notificationCommandService.createWelcomeRegistered(savedAccountMaster);
        return toResponse(savedAccountMaster);
    }

    @Override
    @Transactional
    public AuthLoginResult authenticate(LoginRequest request, HttpServletRequest httpServletRequest, String trustedDeviceKey) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, request.getPassword()));
        AccountMaster accountMaster = getCurrentUserAccount(normalizedEmail);
        if (!accountMaster.isEmailVerified()) {
            throw new ForbiddenException("auth.email_not_verified");
        }

        String trustedDeviceKeyToSet = null;
        if (accountMaster.isTwoFactorEnabled()) {
            boolean trustedDeviceAllowed = isTrustedDeviceAllowed(accountMaster, trustedDeviceKey, httpServletRequest);

            if (!trustedDeviceAllowed) {
                String twoFactorCode = request.getTwoFactorCode() == null ? "" : request.getTwoFactorCode().trim();
                if (twoFactorCode.isBlank()) {
                    throw new BadRequestException("auth.two_factor_required");
                }
                if (!twoFactorCode.matches("^\\d{6}$")) {
                    throw new BadRequestException("auth.two_factor_code_invalid");
                }
                String secret = accountMaster.getTwoFactorSecret();
                if (secret == null || secret.isBlank() || !totpCodeVerifier.isValidCode(secret, twoFactorCode)) {
                    throw new BadRequestException("auth.two_factor_code_invalid");
                }

                if (request.isTrustDevice()) {
                    trustedDeviceKeyToSet = (trustedDeviceKey == null || trustedDeviceKey.isBlank())
                            ? generateTrustedDeviceKey()
                            : trustedDeviceKey;
                    upsertTrustedDevice(accountMaster, trustedDeviceKeyToSet, httpServletRequest);
                }
            }
        }

        accountMaster.setLastLoginAt(Instant.now());
        accountMasterRepository.save(accountMaster);
        return new AuthLoginResult(accountMaster, trustedDeviceKeyToSet);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        return toResponse(getCurrentUserAccount(email));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountMaster getCurrentUserAccount(String email) {
        return accountMasterRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("auth.user_not_found"));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountMaster getCurrentUserAccount(Long accountId) {
        return accountMasterRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("auth.user_not_found"));
    }

    @Override
    @Transactional
    public void revokeAllSessions(String email) {
        AccountMaster accountMaster = getCurrentUserAccount(email);
        accountMaster.setTokenVersion(accountMaster.getTokenVersion() + 1);
        accountSessionService.revokeAllSessions(accountMaster);
        realtimeSessionRevocationService.revokeAccountSessions(accountMaster.getId(), "account_sessions_revoked");
    }

    @Override
    @Transactional
    public UserResponse updatePreferredLanguage(String email, UpdatePreferredLanguageRequest request) {
        SupportedLanguage language = SupportedLanguage.findByCode(request.language())
                .orElseThrow(() -> new BadRequestException("auth.preferred_language_invalid"));
        AccountMaster accountMaster = getCurrentUserAccount(email);
        accountMaster.setPreferredLanguage(language);
        return toResponse(accountMasterRepository.save(accountMaster));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        AccountMaster accountMaster = getCurrentUserAccount(email);
        String currentUsername = accountMaster.getUsername();
        String nextUsername = currentUsername;

        if (request.getUsername() != null) {
            String username = request.getUsername().trim();
            if (username.isBlank()) {
                nextUsername = null;
            } else {
                if (username.length() < USERNAME_MIN_LENGTH || username.length() > USERNAME_MAX_LENGTH) {
                    throw new BadRequestException("auth.username_length_invalid");
                }
                if (!USERNAME_PATTERN.matcher(username).matches()) {
                    throw new BadRequestException("auth.username_format_invalid");
                }
                if (!Objects.equals(currentUsername, username)
                        && accountMasterRepository.countByUsernameExactAndAccountIdNot(username, accountMaster.getId()) > 0L) {
                    throw new ResourceConflictException("auth.username_already_taken");
                }
                nextUsername = username;
            }

            if (!Objects.equals(currentUsername, nextUsername)) {
                Instant now = Instant.now();
                Instant lastUsernameUpdate = accountMaster.getUsernameUpdatedAt();
                Duration usernameChangeCooldown = Duration.ofDays(Math.max(1, profileSettingsProperties.getUsernameChangeCooldownDays()));
                if (lastUsernameUpdate != null && lastUsernameUpdate.plus(usernameChangeCooldown).isAfter(now)) {
                    Instant unlockAt = lastUsernameUpdate.plus(usernameChangeCooldown);
                    long retryAfterSeconds = Duration.between(now, unlockAt).getSeconds();
                    long boundedRetryAfterSeconds = Math.max(1L, retryAfterSeconds);
                    SupportedLanguage language = currentLanguageResolver.resolve();
                    String unlockAtLabel = formatUnlockAt(unlockAt, language);
                    throw new TooManyRequestsException(
                            "auth.username_change_cooldown",
                            boundedRetryAfterSeconds,
                            unlockAtLabel
                    );
                }
                accountMaster.setUsername(nextUsername);
                accountMaster.setUsernameUpdatedAt(now);
            }
        }

        return toResponse(accountMasterRepository.save(accountMaster));
    }

    private UserResponse toResponse(AccountMaster user) {
        String avatarType = user.getAvatarType() == null ? AccountAvatarType.DEFAULT.name() : user.getAvatarType().name();
        String avatarCustomUrl = null;
        if (user.getAvatarType() == AccountAvatarType.CUSTOM && user.getAvatarCustomFileName() != null) {
            long version = user.getAvatarUpdatedAt() == null ? 0L : user.getAvatarUpdatedAt().toEpochMilli();
            avatarCustomUrl = "/api/account/settings/avatar/custom/current?v=" + version;
        }

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                avatarType,
                user.getAvatarPresetPath(),
                avatarCustomUrl,
                user.isEnabled(),
                user.isEmailVerified(),
                user.getPreferredLanguage().getCode(),
                user.getRoles().stream().map(role -> role.getName().name()).collect(Collectors.toSet()),
                user.getCreatedAt());
    }

    private String formatUnlockAt(Instant unlockAt, SupportedLanguage language) {
        Locale locale = resolveLocale(language);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", locale);
        return formatter.format(unlockAt.atZone(ZoneId.systemDefault()));
    }

    private Locale resolveLocale(SupportedLanguage language) {
        if (language == SupportedLanguage.ES) {
            return Locale.forLanguageTag("es-AR");
        }
        if (language == SupportedLanguage.PT) {
            return Locale.forLanguageTag("pt-BR");
        }
        if (language == SupportedLanguage.FR) {
            return Locale.forLanguageTag("fr-FR");
        }
        if (language == SupportedLanguage.DE) {
            return Locale.forLanguageTag("de-DE");
        }
        return Locale.US;
    }

    private boolean isTrustedDeviceAllowed(AccountMaster accountMaster, String trustedDeviceKey, HttpServletRequest request) {
        if (trustedDeviceKey == null || trustedDeviceKey.isBlank()) {
            return false;
        }

        String hash = hashTrustedDeviceKey(trustedDeviceKey);
        return accountTrustedDeviceRepository
                .findByAccount_IdAndDeviceKeyHashAndRevokedAtIsNullAndExpiresAtAfter(accountMaster.getId(), hash, Instant.now())
                .map(device -> {
                    device.setLastUsedAt(Instant.now());
                    device.setIpAddress(clientIpResolver.resolve(request));
                    device.setUserAgent(resolveUserAgent(request));
                    return true;
                })
                .orElse(false);
    }

    private void upsertTrustedDevice(AccountMaster accountMaster, String trustedDeviceKey, HttpServletRequest request) {
        String hash = hashTrustedDeviceKey(trustedDeviceKey);
        AccountTrustedDevice device = accountTrustedDeviceRepository
                .findByAccount_IdAndDeviceKeyHash(accountMaster.getId(), hash)
                .orElseGet(AccountTrustedDevice::new);

        Instant now = Instant.now();
        device.setAccount(accountMaster);
        device.setDeviceKeyHash(hash);
        device.setIpAddress(clientIpResolver.resolve(request));
        device.setUserAgent(resolveUserAgent(request));
        device.setLastUsedAt(now);
        device.setExpiresAt(now.plus(Duration.ofDays(TRUSTED_DEVICE_EXPIRATION_DAYS)));
        device.setRevokedAt(null);
        accountTrustedDeviceRepository.save(device);
    }

    private String resolveUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent;
    }

    private String generateTrustedDeviceKey() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashTrustedDeviceKey(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte hashByte : hashBytes) {
                builder.append(String.format("%02x", hashByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}

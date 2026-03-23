package com.terra.api.auth.application;

import com.terra.api.auth.api.dto.AccountSecurityStatusResponse;
import com.terra.api.auth.api.dto.ConfirmTwoFactorRecoveryRequest;
import com.terra.api.auth.api.dto.TrustedDeviceResponse;
import com.terra.api.auth.api.dto.TwoFactorSetupInitResponse;
import com.terra.api.auth.api.dto.TwoFactorSetupVerifyRequest;
import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.domain.model.AccountTrustedDevice;
import com.terra.api.auth.domain.model.AccountVerification;
import com.terra.api.auth.domain.model.AccountVerificationType;
import com.terra.api.auth.infrastructure.config.TwoFactorRecoveryProperties;
import com.terra.api.auth.infrastructure.persistence.AccountTrustedDeviceRepository;
import com.terra.api.common.domain.exception.ResourceNotFoundException;
import com.terra.api.common.domain.exception.BadRequestException;
import com.terra.api.common.domain.i18n.SupportedLanguage;
import com.terra.api.common.infrastructure.i18n.CurrentLanguageResolver;
import com.terra.api.mail.application.AsyncMailService;
import com.terra.api.mail.application.EmailTemplateService;
import com.terra.api.mail.domain.EmailMessage;
import com.terra.api.mail.infrastructure.config.MailProperties;
import com.terra.api.security.application.AccountSessionService;
import com.terra.api.security.application.ClientIpResolver;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrDataFactory;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AccountSecurityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountSecurityService.class);
    private static final long TWO_FACTOR_RECOVERY_EXPIRATION_MINUTES = 10L;
    private static final long TRUSTED_DEVICE_EXPIRATION_DAYS = 30L;
    private static final String TWO_FACTOR_ISSUER = "Terra";

    private final AuthService authService;
    private final VerificationTokenService verificationTokenService;
    private final EmailTemplateService emailTemplateService;
    private final AsyncMailService asyncMailService;
    private final MailProperties mailProperties;
    private final PasswordEncoder passwordEncoder;
    private final CurrentLanguageResolver currentLanguageResolver;
    private final AccountTrustedDeviceRepository accountTrustedDeviceRepository;
    private final TwoFactorRecoveryProperties twoFactorRecoveryProperties;
    private final AccountSessionService accountSessionService;
    private final ClientIpResolver clientIpResolver;
    private final AtomicLong twoFactorRecoveryTechnicalFailureCounter = new AtomicLong();
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());
    private final ZxingPngQrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountSecurityService(AuthService authService,
                                  VerificationTokenService verificationTokenService,
                                  EmailTemplateService emailTemplateService,
                                  AsyncMailService asyncMailService,
                                  MailProperties mailProperties,
                                  PasswordEncoder passwordEncoder,
                                  CurrentLanguageResolver currentLanguageResolver,
                                  AccountTrustedDeviceRepository accountTrustedDeviceRepository,
                                  TwoFactorRecoveryProperties twoFactorRecoveryProperties,
                                  AccountSessionService accountSessionService,
                                  ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.verificationTokenService = verificationTokenService;
        this.emailTemplateService = emailTemplateService;
        this.asyncMailService = asyncMailService;
        this.mailProperties = mailProperties;
        this.passwordEncoder = passwordEncoder;
        this.currentLanguageResolver = currentLanguageResolver;
        this.accountTrustedDeviceRepository = accountTrustedDeviceRepository;
        this.twoFactorRecoveryProperties = twoFactorRecoveryProperties;
        this.accountSessionService = accountSessionService;
        this.clientIpResolver = clientIpResolver;
    }

    @Transactional(readOnly = true)
    public AccountSecurityStatusResponse getStatus(String email) {
        AccountMaster accountMaster = authService.getCurrentUserAccount(email);
        return new AccountSecurityStatusResponse(
                accountMaster.isTwoFactorEnabled(),
                accountMaster.getTwoFactorEnabledAt(),
                accountMaster.getTwoFactorRecoveryRequestedAt()
        );
    }

    @Transactional
    public TwoFactorSetupInitResponse initTwoFactorSetup(String email) {
        AccountMaster accountMaster = authService.getCurrentUserAccount(email);
        String secret = secretGenerator.generate();
        accountMaster.setTwoFactorSecret(secret);

        QrData data = new QrData.Builder()
                .label(accountMaster.getEmail())
                .secret(secret)
                .issuer(TWO_FACTOR_ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        byte[] qrBytes;
        try {
            qrBytes = qrGenerator.generate(data);
        } catch (QrGenerationException exception) {
            throw new IllegalStateException("Unable to generate 2FA QR image", exception);
        }
        String qrDataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(qrBytes);

        return new TwoFactorSetupInitResponse(secret, data.getUri(), qrDataUrl);
    }

    @Transactional
    public TwoFactorSetupVerificationResult verifyTwoFactorSetup(String email,
                                                                 TwoFactorSetupVerifyRequest request,
                                                                 HttpServletRequest httpServletRequest,
                                                                 String trustedDeviceKey,
                                                                 Long keepSessionId) {
        AccountMaster accountMaster = authService.getCurrentUserAccount(email);

        String secret = accountMaster.getTwoFactorSecret();
        if (secret == null || secret.isBlank()) {
            throw new BadRequestException("auth.two_factor_setup_not_initialized");
        }

        String code = request.getCode().trim();
        if (!code.matches("^\\d{6}$")) {
            throw new BadRequestException("auth.two_factor_code_invalid");
        }

        if (!codeVerifier.isValidCode(secret, code)) {
            throw new BadRequestException("auth.two_factor_code_invalid");
        }

        String keyToSet = (trustedDeviceKey == null || trustedDeviceKey.isBlank())
                ? generateTrustedDeviceKey()
                : trustedDeviceKey;

        if (accountMaster.isTwoFactorEnabled()) {
            upsertTrustedDevice(accountMaster, keyToSet, httpServletRequest);
            return new TwoFactorSetupVerificationResult(accountMaster, keyToSet);
        }

        accountMaster.setTwoFactorEnabled(true);
        accountMaster.setTwoFactorEnabledAt(Instant.now());
        accountMaster.setTokenVersion(accountMaster.getTokenVersion() + 1);
        accountSessionService.revokeAllSessionsExcept(accountMaster, keepSessionId);
        revokeAllTrustedDevices(accountMaster);

        upsertTrustedDevice(accountMaster, keyToSet, httpServletRequest);
        return new TwoFactorSetupVerificationResult(accountMaster, keyToSet);
    }

    @Transactional
    public void requestTwoFactorRecovery(String email) {
        AccountMaster accountMaster = authService.getCurrentUserAccount(email);
        if (!accountMaster.isTwoFactorEnabled()) {
            throw new BadRequestException("auth.two_factor_not_enabled");
        }
        sendTwoFactorRecoveryEmail(accountMaster);
    }

    @Transactional
    public void requestTwoFactorRecoveryIfPossible(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Long accountId = null;
        try {
            AccountMaster accountMaster = authService.getCurrentUserAccount(normalizedEmail);
            accountId = accountMaster.getId();
            if (!accountMaster.isTwoFactorEnabled()) {
                return;
            }
            if (isRecoveryRequestInCooldown(accountMaster)) {
                return;
            }
            sendTwoFactorRecoveryEmail(accountMaster);
        } catch (ResourceNotFoundException ignored) {
            return;
        } catch (RuntimeException exception) {
            logTwoFactorRecoveryTechnicalFailure(accountId, normalizedEmail, exception);
        }
    }

    @Transactional
    public void confirmTwoFactorRecovery(ConfirmTwoFactorRecoveryRequest request) {
        AccountVerification verification = verificationTokenService.getActiveVerification(
                request.getToken(),
                AccountVerificationType.TWO_FACTOR_RECOVERY,
                "auth.invalid_two_factor_recovery_token"
        );

        AccountMaster accountMaster = verification.getAccount();
        if (!passwordEncoder.matches(request.getCurrentPassword(), accountMaster.getPasswordHash())) {
            throw new BadRequestException("auth.invalid_credentials");
        }

        accountMaster.setTwoFactorEnabled(false);
        accountMaster.setTwoFactorSecret(null);
        accountMaster.setTwoFactorEnabledAt(null);
        verification.setUsedAt(Instant.now());
    }

    private void sendTwoFactorRecoveryEmail(AccountMaster accountMaster) {
        SupportedLanguage language = currentLanguageResolver.resolve();

        String rawToken = verificationTokenService.createOrRefresh(
                accountMaster,
                AccountVerificationType.TWO_FACTOR_RECOVERY,
                TWO_FACTOR_RECOVERY_EXPIRATION_MINUTES
        );

        String securityUrl = mailProperties.getFrontendTwoFactorRecoveryUrl() + "?token=" + rawToken;
        EmailMessage emailMessage = emailTemplateService.buildTwoFactorRecoveryMessage(
                accountMaster.getEmail(),
                securityUrl,
                language,
                TWO_FACTOR_RECOVERY_EXPIRATION_MINUTES
        );

        accountMaster.setTwoFactorRecoveryRequestedAt(Instant.now());
        asyncMailService.sendHtml(accountMaster.getEmail(), emailMessage.subject(), emailMessage.htmlBody());
    }

    @Transactional
    public void confirmTwoFactorRecovery(String email, ConfirmTwoFactorRecoveryRequest request) {
        AccountMaster authenticated = authService.getCurrentUserAccount(email);
        AccountVerification verification = verificationTokenService.getActiveVerification(
                request.getToken(),
                AccountVerificationType.TWO_FACTOR_RECOVERY,
                "auth.invalid_two_factor_recovery_token"
        );

        if (!verification.getAccount().getId().equals(authenticated.getId())) {
            throw new BadRequestException("auth.invalid_two_factor_recovery_token");
        }
        confirmTwoFactorRecovery(request);
    }

    @Transactional(readOnly = true)
    public List<TrustedDeviceResponse> listTrustedDevices(String email, HttpServletRequest request) {
        AccountMaster accountMaster = authService.getCurrentUserAccount(email);
        String currentIp = clientIpResolver.resolve(request);
        String currentUserAgent = request.getHeader("User-Agent");

        return accountTrustedDeviceRepository
                .findByAccount_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDescCreatedAtDesc(accountMaster.getId(), Instant.now())
                .stream()
                .map(device -> new TrustedDeviceResponse(
                        device.getId(),
                        device.getIpAddress(),
                        device.getUserAgent(),
                        device.getCreatedAt(),
                        device.getExpiresAt(),
                        isCurrentDevice(currentIp, currentUserAgent, device)
                ))
                .toList();
    }

    @Transactional
    public void revokeTrustedDevice(String email, Long deviceId) {
        AccountMaster accountMaster = authService.getCurrentUserAccount(email);
        AccountTrustedDevice device = accountTrustedDeviceRepository.findByIdAndAccount_Id(deviceId, accountMaster.getId())
                .orElseThrow(() -> new BadRequestException("auth.invalid_token"));
        if (device.getRevokedAt() == null) {
            device.setRevokedAt(Instant.now());
        }
    }

    @Transactional
    public void revokeAllTrustedDevices(String email) {
        AccountMaster accountMaster = authService.getCurrentUserAccount(email);
        revokeAllTrustedDevices(accountMaster);
    }

    private void revokeAllTrustedDevices(AccountMaster accountMaster) {
        Instant revokedAt = Instant.now();
        accountTrustedDeviceRepository.findByAccount_IdAndRevokedAtIsNull(accountMaster.getId())
                .forEach(device -> device.setRevokedAt(revokedAt));
    }

    private boolean isRecoveryRequestInCooldown(AccountMaster accountMaster) {
        Instant requestedAt = accountMaster.getTwoFactorRecoveryRequestedAt();
        if (requestedAt == null) {
            return false;
        }

        int configuredMinutes = Math.max(1, twoFactorRecoveryProperties.getRequestCooldownMinutes());
        Instant allowedAt = requestedAt.plus(Duration.ofMinutes(configuredMinutes));
        return allowedAt.isAfter(Instant.now());
    }

    private void logTwoFactorRecoveryTechnicalFailure(Long accountId, String email, RuntimeException exception) {
        String requestId = UUID.randomUUID().toString();
        long failureCount = twoFactorRecoveryTechnicalFailureCounter.incrementAndGet();
        LOGGER.error(
                "2FA recovery request failed requestId={} accountId={} email={} failureCount={}",
                requestId,
                accountId,
                email,
                failureCount,
                exception
        );
    }

    private boolean isCurrentDevice(String currentIp, String currentUserAgent, AccountTrustedDevice device) {
        if (currentIp == null || currentUserAgent == null) {
            return false;
        }
        return currentIp.equals(device.getIpAddress()) && currentUserAgent.equals(device.getUserAgent());
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

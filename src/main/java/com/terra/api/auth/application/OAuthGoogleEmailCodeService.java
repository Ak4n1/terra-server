package com.terra.api.auth.application;

import com.terra.api.auth.domain.model.OAuthGoogleEmailCodeChallenge;
import com.terra.api.auth.infrastructure.config.OAuthGoogleEmailCodeProperties;
import com.terra.api.auth.infrastructure.persistence.OAuthGoogleEmailCodeChallengeRepository;
import com.terra.api.common.domain.exception.BadRequestException;
import com.terra.api.common.domain.exception.TooManyRequestsException;
import com.terra.api.common.domain.i18n.SupportedLanguage;
import com.terra.api.mail.application.AsyncMailService;
import com.terra.api.mail.application.EmailActionCooldownService;
import com.terra.api.mail.application.EmailTemplateService;
import com.terra.api.mail.domain.EmailMessage;
import com.terra.api.mail.infrastructure.config.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class OAuthGoogleEmailCodeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthGoogleEmailCodeService.class);
    private static final int CODE_MIN = 100_000;
    private static final int CODE_MAX_EXCLUSIVE = 1_000_000;
    private static final String EMAIL_ACTION = "oauth-google-email-code";

    private final OAuthGoogleEmailCodeChallengeRepository challengeRepository;
    private final OAuthGoogleEmailCodeProperties properties;
    private final EmailActionCooldownService emailActionCooldownService;
    private final EmailTemplateService emailTemplateService;
    private final AsyncMailService asyncMailService;
    private final MailProperties mailProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuthGoogleEmailCodeService(OAuthGoogleEmailCodeChallengeRepository challengeRepository,
                                       OAuthGoogleEmailCodeProperties properties,
                                       EmailActionCooldownService emailActionCooldownService,
                                       EmailTemplateService emailTemplateService,
                                       AsyncMailService asyncMailService,
                                       MailProperties mailProperties) {
        this.challengeRepository = challengeRepository;
        this.properties = properties;
        this.emailActionCooldownService = emailActionCooldownService;
        this.emailTemplateService = emailTemplateService;
        this.asyncMailService = asyncMailService;
        this.mailProperties = mailProperties;
    }

    @Transactional
    public ChallengeDetails issueChallenge(String providerSubject, String email, SupportedLanguage language) {
        String normalizedSubject = normalizeSubject(providerSubject);
        String normalizedEmail = normalizeEmail(email);
        Instant now = Instant.now();
        long cooldownSeconds = Math.max(0L, properties.getResendCooldownSeconds());

        Optional<OAuthGoogleEmailCodeChallenge> existing = challengeRepository.findByProviderSubject(normalizedSubject);
        if (existing.isPresent()) {
            OAuthGoogleEmailCodeChallenge challenge = existing.get();
            if (challenge.isActive(now)
                    && emailActionCooldownService.getRetryAfterSeconds(EMAIL_ACTION, normalizedEmail, cooldownSeconds) > 0) {
                LOGGER.info(
                        "[OAUTH-GOOGLE] service=OAuthGoogleEmailCodeService action=issue-challenge decision=reuse-existing challengeId={} email={} subjectFingerprint={}",
                        challenge.getChallengeId(),
                        maskEmail(challenge.getEmail()),
                        fingerprint(normalizedSubject)
                );
                return new ChallengeDetails(challenge.getChallengeId(), maskEmail(challenge.getEmail()));
            }
        }

        String rawCode = generateCode();
        OAuthGoogleEmailCodeChallenge challenge = existing.orElseGet(OAuthGoogleEmailCodeChallenge::new);
        challenge.setChallengeId(UUID.randomUUID().toString());
        challenge.setProviderSubject(normalizedSubject);
        challenge.setEmail(normalizedEmail);
        challenge.setLanguage(language == null ? SupportedLanguage.defaultLanguage() : language);
        challenge.setCodeHash(hash(rawCode));
        challenge.setExpiresAt(now.plus(Math.max(1, properties.getExpirationMinutes()), ChronoUnit.MINUTES));
        challenge.setAttemptCount(0);
        challenge.setConsumedAt(null);
        challengeRepository.save(challenge);
        LOGGER.info(
                "[OAUTH-GOOGLE] service=OAuthGoogleEmailCodeService action=issue-challenge decision=issued challengeId={} email={} subjectFingerprint={} expiresAt={}",
                challenge.getChallengeId(),
                maskEmail(challenge.getEmail()),
                fingerprint(normalizedSubject),
                challenge.getExpiresAt()
        );

        emailActionCooldownService.mark(EMAIL_ACTION, normalizedEmail, cooldownSeconds);
        sendCodeEmail(challenge, rawCode);

        return new ChallengeDetails(challenge.getChallengeId(), maskEmail(normalizedEmail));
    }

    @Transactional
    public ChallengeDetails resendCode(String challengeId) {
        OAuthGoogleEmailCodeChallenge challenge = challengeRepository.findByChallengeId(challengeId).orElse(null);
        if (challenge == null) {
            LOGGER.warn(
                    "[OAUTH-GOOGLE] service=OAuthGoogleEmailCodeService action=resend-code result=challenge-not-found challengeId={}",
                    challengeId
            );
            throw new BadRequestException("auth.oauth_google_email_code_invalid");
        }
        Instant now = Instant.now();
        if (!challenge.isActive(now)) {
            LOGGER.warn(
                    "[OAUTH-GOOGLE] service=OAuthGoogleEmailCodeService action=resend-code result=challenge-inactive challengeId={} email={} expiresAt={} consumedAt={}",
                    challengeId,
                    maskEmail(challenge.getEmail()),
                    challenge.getExpiresAt(),
                    challenge.getConsumedAt()
            );
            throw new BadRequestException("auth.oauth_google_email_code_invalid");
        }

        long cooldownSeconds = Math.max(0L, properties.getResendCooldownSeconds());
        long retryAfterSeconds = emailActionCooldownService.getRetryAfterSeconds(
                EMAIL_ACTION,
                challenge.getEmail(),
                cooldownSeconds
        );
        if (retryAfterSeconds > 0) {
            LOGGER.warn(
                    "[OAUTH-GOOGLE] service=OAuthGoogleEmailCodeService action=resend-code result=cooldown challengeId={} email={} retryAfterSeconds={}",
                    challengeId,
                    maskEmail(challenge.getEmail()),
                    retryAfterSeconds
            );
            throw new TooManyRequestsException("auth.oauth_google_email_code_cooldown_active", retryAfterSeconds);
        }

        String rawCode = generateCode();
        challenge.setCodeHash(hash(rawCode));
        challenge.setExpiresAt(now.plus(Math.max(1, properties.getExpirationMinutes()), ChronoUnit.MINUTES));
        challenge.setAttemptCount(0);
        challengeRepository.save(challenge);
        LOGGER.info(
                "[OAUTH-GOOGLE] service=OAuthGoogleEmailCodeService action=resend-code result=sent challengeId={} email={} expiresAt={}",
                challenge.getChallengeId(),
                maskEmail(challenge.getEmail()),
                challenge.getExpiresAt()
        );

        emailActionCooldownService.mark(EMAIL_ACTION, challenge.getEmail(), cooldownSeconds);
        sendCodeEmail(challenge, rawCode);

        return new ChallengeDetails(challenge.getChallengeId(), maskEmail(challenge.getEmail()));
    }

    @Transactional
    public VerifiedChallenge verifyCode(String challengeId, String code) {
        OAuthGoogleEmailCodeChallenge challenge = challengeRepository.findByChallengeId(challengeId).orElse(null);
        if (challenge == null) {
            LOGGER.warn(
                    "[OAUTH-GOOGLE] service=OAuthGoogleEmailCodeService action=verify-code result=challenge-not-found challengeId={}",
                    challengeId
            );
            throw new BadRequestException("auth.oauth_google_email_code_invalid");
        }
        Instant now = Instant.now();
        if (!challenge.isActive(now)) {
            LOGGER.warn(
                    "[OAUTH-GOOGLE] service=OAuthGoogleEmailCodeService action=verify-code result=challenge-inactive challengeId={} email={} expiresAt={} consumedAt={}",
                    challengeId,
                    maskEmail(challenge.getEmail()),
                    challenge.getExpiresAt(),
                    challenge.getConsumedAt()
            );
            throw new BadRequestException("auth.oauth_google_email_code_invalid");
        }

        int maxVerifyAttempts = Math.max(1, properties.getMaxVerifyAttempts());
        if (challenge.getAttemptCount() >= maxVerifyAttempts) {
            long retryAfterSeconds = Math.max(1L, now.until(challenge.getExpiresAt(), ChronoUnit.SECONDS));
            LOGGER.warn(
                    "[OAUTH-GOOGLE] service=OAuthGoogleEmailCodeService action=verify-code result=attempts-exceeded challengeId={} email={} attempts={} maxAttempts={} retryAfterSeconds={}",
                    challengeId,
                    maskEmail(challenge.getEmail()),
                    challenge.getAttemptCount(),
                    maxVerifyAttempts,
                    retryAfterSeconds
            );
            throw new TooManyRequestsException("auth.oauth_google_email_code_attempts_exceeded", retryAfterSeconds);
        }

        String normalizedCode = code == null ? "" : code.trim();
        if (!hash(normalizedCode).equals(challenge.getCodeHash())) {
            challenge.setAttemptCount(challenge.getAttemptCount() + 1);
            challengeRepository.save(challenge);
            LOGGER.warn(
                    "[OAUTH-GOOGLE] service=OAuthGoogleEmailCodeService action=verify-code result=invalid-code challengeId={} email={} attempts={} maxAttempts={}",
                    challengeId,
                    maskEmail(challenge.getEmail()),
                    challenge.getAttemptCount(),
                    maxVerifyAttempts
            );

            if (challenge.getAttemptCount() >= maxVerifyAttempts) {
                long retryAfterSeconds = Math.max(1L, now.until(challenge.getExpiresAt(), ChronoUnit.SECONDS));
                throw new TooManyRequestsException("auth.oauth_google_email_code_attempts_exceeded", retryAfterSeconds);
            }
            throw new BadRequestException("auth.oauth_google_email_code_invalid");
        }

        challenge.setConsumedAt(now);
        challengeRepository.save(challenge);
        LOGGER.info(
                "[OAUTH-GOOGLE] service=OAuthGoogleEmailCodeService action=verify-code result=success challengeId={} email={} subjectFingerprint={}",
                challenge.getChallengeId(),
                maskEmail(challenge.getEmail()),
                fingerprint(challenge.getProviderSubject())
        );
        return new VerifiedChallenge(challenge.getProviderSubject(), challenge.getEmail());
    }

    private void sendCodeEmail(OAuthGoogleEmailCodeChallenge challenge, String rawCode) {
        EmailMessage emailMessage = emailTemplateService.buildOAuthGoogleEmailCodeMessage(
                challenge.getEmail(),
                rawCode,
                resolveFrontendAuthUrl(),
                challenge.getLanguage(),
                Math.max(1, properties.getExpirationMinutes())
        );
        LOGGER.info(
                "[OAUTH-GOOGLE] service=OAuthGoogleEmailCodeService action=send-email challengeId={} email={} subject={}",
                challenge.getChallengeId(),
                maskEmail(challenge.getEmail()),
                emailMessage.subject()
        );
        asyncMailService.sendHtml(challenge.getEmail(), emailMessage.subject(), emailMessage.htmlBody());
    }

    private String resolveFrontendAuthUrl() {
        String configuredUrl = mailProperties.getFrontendAuthUrl();
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return "https://l2terra.online/register";
        }
        return configuredUrl;
    }

    private String generateCode() {
        return String.valueOf(secureRandom.nextInt(CODE_MIN, CODE_MAX_EXCLUSIVE));
    }

    private String hash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte hashByte : digest) {
                builder.append(String.format("%02x", hashByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSubject(String providerSubject) {
        return providerSubject == null ? "" : providerSubject.trim();
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1 || atIndex == email.length() - 1) {
            return "***";
        }

        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex + 1);

        String maskedLocal = localPart.charAt(0) + "***";
        int domainDot = domainPart.lastIndexOf('.');
        if (domainDot <= 1) {
            return maskedLocal + "@***";
        }

        String domainName = domainPart.substring(0, domainDot);
        String domainSuffix = domainPart.substring(domainDot);
        String maskedDomain = domainName.charAt(0) + "***";
        return maskedLocal + "@" + maskedDomain + domainSuffix;
    }

    private String fingerprint(String value) {
        if (value == null || value.isBlank()) {
            return "empty";
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 8 && index < digest.length; index++) {
                builder.append(String.format("%02x", digest[index]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            return "sha256-unavailable";
        }
    }

    public record ChallengeDetails(
            String challengeId,
            String maskedEmail
    ) {
    }

    public record VerifiedChallenge(
            String providerSubject,
            String email
    ) {
    }
}

package com.terra.api.auth.service;

import com.terra.api.auth.dto.ResetPasswordRequest;
import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.AccountVerification;
import com.terra.api.auth.entity.AccountVerificationType;
import com.terra.api.auth.repository.AccountMasterRepository;
import com.terra.api.common.exception.TooManyRequestsException;
import com.terra.api.common.i18n.model.SupportedLanguage;
import com.terra.api.common.i18n.resolver.CurrentLanguageResolver;
import com.terra.api.mail.config.MailProperties;
import com.terra.api.mail.service.AsyncMailService;
import com.terra.api.mail.service.EmailActionCooldownService;
import com.terra.api.mail.service.EmailMessage;
import com.terra.api.mail.service.EmailTemplateService;
import com.terra.api.security.service.AccountSessionService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class PasswordResetService {

    private static final long PASSWORD_RESET_EXPIRATION_MINUTES = 15L;
    private static final String PASSWORD_RESET_ACTION = "forgot-password";

    private final AccountMasterRepository accountMasterRepository;
    private final VerificationTokenService verificationTokenService;
    private final EmailTemplateService emailTemplateService;
    private final AsyncMailService asyncMailService;
    private final MailProperties mailProperties;
    private final EmailActionCooldownService emailActionCooldownService;
    private final CurrentLanguageResolver currentLanguageResolver;
    private final PasswordEncoder passwordEncoder;
    private final AccountSessionService accountSessionService;

    public PasswordResetService(AccountMasterRepository accountMasterRepository,
                                VerificationTokenService verificationTokenService,
                                EmailTemplateService emailTemplateService,
                                AsyncMailService asyncMailService,
                                MailProperties mailProperties,
                                EmailActionCooldownService emailActionCooldownService,
                                CurrentLanguageResolver currentLanguageResolver,
                                PasswordEncoder passwordEncoder,
                                AccountSessionService accountSessionService) {
        this.accountMasterRepository = accountMasterRepository;
        this.verificationTokenService = verificationTokenService;
        this.emailTemplateService = emailTemplateService;
        this.asyncMailService = asyncMailService;
        this.mailProperties = mailProperties;
        this.emailActionCooldownService = emailActionCooldownService;
        this.currentLanguageResolver = currentLanguageResolver;
        this.passwordEncoder = passwordEncoder;
        this.accountSessionService = accountSessionService;
    }

    @Transactional
    public void sendResetPasswordEmailIfPossible(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        long retryAfterSeconds = emailActionCooldownService.getRetryAfterSeconds(
                PASSWORD_RESET_ACTION,
                normalizedEmail,
                mailProperties.getPasswordResetCooldownSeconds()
        );
        if (retryAfterSeconds > 0) {
            throw new TooManyRequestsException("auth.password_reset_cooldown_active", retryAfterSeconds);
        }

        emailActionCooldownService.mark(
                PASSWORD_RESET_ACTION,
                normalizedEmail,
                mailProperties.getPasswordResetCooldownSeconds()
        );

        AccountMaster accountMaster = accountMasterRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (accountMaster == null) {
            return;
        }

        SupportedLanguage language = currentLanguageResolver.resolve();
        String rawToken = verificationTokenService.createOrRefresh(
                accountMaster,
                AccountVerificationType.PASSWORD_RESET,
                PASSWORD_RESET_EXPIRATION_MINUTES
        );

        String resetUrl = mailProperties.getFrontendResetPasswordUrl() + "?token=" + rawToken;
        EmailMessage emailMessage = emailTemplateService.buildPasswordResetMessage(
                accountMaster.getEmail(),
                resetUrl,
                language,
                PASSWORD_RESET_EXPIRATION_MINUTES
        );

        asyncMailService.sendHtml(accountMaster.getEmail(), emailMessage.subject(), emailMessage.htmlBody());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        AccountVerification verification = verificationTokenService.getActiveVerification(
                request.getToken(),
                AccountVerificationType.PASSWORD_RESET,
                "auth.invalid_password_reset_token"
        );

        AccountMaster accountMaster = verification.getAccount();
        accountMaster.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        accountMaster.setTokenVersion(accountMaster.getTokenVersion() + 1);
        verification.setUsedAt(Instant.now());
        accountSessionService.revokeAllSessions(accountMaster);
    }
}

package com.terra.api.auth.service;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.AccountVerification;
import com.terra.api.auth.entity.AccountVerificationType;
import com.terra.api.auth.repository.AccountMasterRepository;
import com.terra.api.common.exception.TooManyRequestsException;
import com.terra.api.common.i18n.model.SupportedLanguage;
import com.terra.api.common.i18n.resolver.CurrentLanguageResolver;
import com.terra.api.common.exception.ResourceConflictException;
import com.terra.api.common.exception.ResourceNotFoundException;
import com.terra.api.mail.config.MailProperties;
import com.terra.api.mail.service.AsyncMailService;
import com.terra.api.mail.service.EmailActionCooldownService;
import com.terra.api.mail.service.EmailMessage;
import com.terra.api.mail.service.EmailTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class EmailVerificationService {

    private static final long EMAIL_VERIFICATION_EXPIRATION_MINUTES = 15L;
    private static final String EMAIL_ACTION = "resend-verification";

    private final AccountMasterRepository accountMasterRepository;
    private final VerificationTokenService verificationTokenService;
    private final AsyncMailService asyncMailService;
    private final EmailTemplateService emailTemplateService;
    private final MailProperties mailProperties;
    private final EmailActionCooldownService emailActionCooldownService;
    private final CurrentLanguageResolver currentLanguageResolver;

    public EmailVerificationService(AccountMasterRepository accountMasterRepository,
                                    VerificationTokenService verificationTokenService,
                                    AsyncMailService asyncMailService,
                                    EmailTemplateService emailTemplateService,
                                    MailProperties mailProperties,
                                    EmailActionCooldownService emailActionCooldownService,
                                    CurrentLanguageResolver currentLanguageResolver) {
        this.accountMasterRepository = accountMasterRepository;
        this.verificationTokenService = verificationTokenService;
        this.asyncMailService = asyncMailService;
        this.emailTemplateService = emailTemplateService;
        this.mailProperties = mailProperties;
        this.emailActionCooldownService = emailActionCooldownService;
        this.currentLanguageResolver = currentLanguageResolver;
    }

    @Transactional
    public void createOrRefreshEmailVerification(AccountMaster accountMaster) {
        SupportedLanguage language = currentLanguageResolver.resolve();
        String rawToken = verificationTokenService.createOrRefresh(
                accountMaster,
                AccountVerificationType.EMAIL_VERIFICATION,
                EMAIL_VERIFICATION_EXPIRATION_MINUTES
        );
        String verificationUrl = mailProperties.getFrontendVerifyUrl() + "?token=" + rawToken;
        EmailMessage emailMessage = emailTemplateService.buildEmailVerificationMessage(
                accountMaster.getEmail(),
                verificationUrl,
                language,
                EMAIL_VERIFICATION_EXPIRATION_MINUTES
        );
        asyncMailService.sendHtml(accountMaster.getEmail(), emailMessage.subject(), emailMessage.htmlBody());
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        AccountVerification verification = verificationTokenService.getActiveVerification(
                rawToken,
                AccountVerificationType.EMAIL_VERIFICATION,
                "auth.invalid_verification_token"
        );
        AccountMaster accountMaster = verification.getAccount();
        accountMaster.setEmailVerified(true);
        verification.setUsedAt(Instant.now());
        accountMasterRepository.save(accountMaster);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        long cooldownSeconds = mailProperties.getVerificationEmailCooldownSeconds();
        long retryAfterSeconds = emailActionCooldownService.getRetryAfterSeconds(
                EMAIL_ACTION,
                normalizedEmail,
                cooldownSeconds
        );
        if (retryAfterSeconds > 0) {
            throw new TooManyRequestsException("auth.verification_email_cooldown_active", retryAfterSeconds);
        }

        emailActionCooldownService.mark(EMAIL_ACTION, normalizedEmail, cooldownSeconds);
        AccountMaster accountMaster = accountMasterRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("auth.user_not_found"));

        if (accountMaster.isEmailVerified()) {
            throw new ResourceConflictException("auth.email_already_verified");
        }

        createOrRefreshEmailVerification(accountMaster);
    }
}

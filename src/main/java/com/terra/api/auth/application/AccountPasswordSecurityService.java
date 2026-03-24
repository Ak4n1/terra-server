package com.terra.api.auth.application;

import com.terra.api.auth.api.dto.ChangeAccountPasswordRequest;
import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.domain.model.AccountVerificationType;
import com.terra.api.common.domain.exception.BadRequestException;
import com.terra.api.common.domain.exception.TooManyRequestsException;
import com.terra.api.common.domain.i18n.SupportedLanguage;
import com.terra.api.common.infrastructure.i18n.CurrentLanguageResolver;
import com.terra.api.mail.application.AsyncMailService;
import com.terra.api.mail.application.EmailActionCooldownService;
import com.terra.api.mail.application.EmailTemplateService;
import com.terra.api.mail.domain.EmailMessage;
import com.terra.api.mail.infrastructure.config.MailProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AccountPasswordSecurityService {
    private static final long PASSWORD_RESET_EXPIRATION_MINUTES = 15L;
    private static final String PASSWORD_RESET_ACTION = "account-settings-password-reset";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final VerificationTokenService verificationTokenService;
    private final EmailTemplateService emailTemplateService;
    private final AsyncMailService asyncMailService;
    private final MailProperties mailProperties;
    private final EmailActionCooldownService emailActionCooldownService;
    private final CurrentLanguageResolver currentLanguageResolver;
    private final PasswordEncoder passwordEncoder;

    public AccountPasswordSecurityService(AuthService authService,
                                          PasswordResetService passwordResetService,
                                          VerificationTokenService verificationTokenService,
                                          EmailTemplateService emailTemplateService,
                                          AsyncMailService asyncMailService,
                                          MailProperties mailProperties,
                                          EmailActionCooldownService emailActionCooldownService,
                                          CurrentLanguageResolver currentLanguageResolver,
                                          PasswordEncoder passwordEncoder) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.verificationTokenService = verificationTokenService;
        this.emailTemplateService = emailTemplateService;
        this.asyncMailService = asyncMailService;
        this.mailProperties = mailProperties;
        this.emailActionCooldownService = emailActionCooldownService;
        this.currentLanguageResolver = currentLanguageResolver;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(String authenticatedEmail, ChangeAccountPasswordRequest request) {
        AccountMaster accountMaster = authService.getCurrentUserAccount(authenticatedEmail);
        if (!passwordEncoder.matches(request.getCurrentPassword(), accountMaster.getPasswordHash())) {
            throw new BadRequestException("auth.invalid_credentials");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("auth.password_mismatch");
        }

        applyPasswordUpdate(accountMaster, request.getNewPassword(), "password_changed");
    }

    @Transactional
    public void requestResetEmail(String authenticatedEmail) {
        AccountMaster accountMaster = authService.getCurrentUserAccount(authenticatedEmail);
        String normalizedEmail = accountMaster.getEmail().trim().toLowerCase(Locale.ROOT);
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

        SupportedLanguage language = currentLanguageResolver.resolve();
        String rawToken = verificationTokenService.createOrRefresh(
                accountMaster,
                AccountVerificationType.PASSWORD_RESET,
                PASSWORD_RESET_EXPIRATION_MINUTES
        );

        String resetBaseUrl = mailProperties.getFrontendSecurityUrl();
        if (resetBaseUrl == null || resetBaseUrl.isBlank()) {
            resetBaseUrl = mailProperties.getFrontendResetPasswordUrl();
        }
        String resetUrl = resetBaseUrl + (resetBaseUrl.contains("?") ? "&" : "?") + "token=" + rawToken;

        EmailMessage emailMessage = emailTemplateService.buildPasswordResetMessage(
                accountMaster.getEmail(),
                resetUrl,
                language,
                PASSWORD_RESET_EXPIRATION_MINUTES
        );
        asyncMailService.sendHtml(accountMaster.getEmail(), emailMessage.subject(), emailMessage.htmlBody());
    }

    private void applyPasswordUpdate(AccountMaster accountMaster, String newPassword, String realtimeReason) {
        passwordResetService.applyPasswordReset(accountMaster, newPassword, realtimeReason);
    }
}

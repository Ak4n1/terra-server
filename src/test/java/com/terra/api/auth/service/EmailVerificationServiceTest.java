package com.terra.api.auth.service;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.AccountVerification;
import com.terra.api.auth.entity.AccountVerificationType;
import com.terra.api.auth.repository.AccountMasterRepository;
import com.terra.api.common.exception.BadRequestException;
import com.terra.api.common.exception.TooManyRequestsException;
import com.terra.api.common.i18n.model.SupportedLanguage;
import com.terra.api.common.i18n.resolver.CurrentLanguageResolver;
import com.terra.api.mail.config.MailProperties;
import com.terra.api.mail.service.AsyncMailService;
import com.terra.api.mail.service.EmailActionCooldownService;
import com.terra.api.mail.service.EmailMessage;
import com.terra.api.mail.service.EmailTemplateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private AccountMasterRepository accountMasterRepository;

    @Mock
    private VerificationTokenService verificationTokenService;

    @Mock
    private AsyncMailService asyncMailService;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private MailProperties mailProperties;

    @Mock
    private EmailActionCooldownService emailActionCooldownService;

    @Mock
    private CurrentLanguageResolver currentLanguageResolver;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @Test
    void shouldCreateOrRefreshVerificationAndSendEmail() {
        AccountMaster accountMaster = account(15L, "user@l2terra.online");
        when(currentLanguageResolver.resolve()).thenReturn(SupportedLanguage.US);
        when(verificationTokenService.createOrRefresh(accountMaster, AccountVerificationType.EMAIL_VERIFICATION, 15L))
                .thenReturn("raw-token");
        when(mailProperties.getFrontendVerifyUrl()).thenReturn("https://l2terra.online/verify-email");
        when(emailTemplateService.buildEmailVerificationMessage(any(), any(), any(), any(Long.class)))
                .thenReturn(new EmailMessage("Verify your L2 Terra account", "<html>mail</html>"));

        emailVerificationService.createOrRefreshEmailVerification(accountMaster);

        verify(asyncMailService).sendHtml("user@l2terra.online", "Verify your L2 Terra account", "<html>mail</html>");
    }

    @Test
    void shouldMarkEmailAsVerifiedWhenTokenIsValid() {
        AccountMaster accountMaster = account(15L, "user@l2terra.online");
        AccountVerification verification = new AccountVerification();
        verification.setAccount(accountMaster);
        verification.setType(AccountVerificationType.EMAIL_VERIFICATION);
        verification.setExpiresAt(Instant.now().plusSeconds(60));

        when(verificationTokenService.getActiveVerification(
                "valid-token",
                AccountVerificationType.EMAIL_VERIFICATION,
                "auth.invalid_verification_token"
        )).thenReturn(verification);

        emailVerificationService.verifyEmail("valid-token");

        assertTrue(accountMaster.isEmailVerified());
        verify(accountMasterRepository).save(accountMaster);
    }

    @Test
    void shouldRejectVerificationWhenTokenIsInvalid() {
        when(verificationTokenService.getActiveVerification(
                "bad-token",
                AccountVerificationType.EMAIL_VERIFICATION,
                "auth.invalid_verification_token"
        )).thenThrow(new BadRequestException("auth.invalid_verification_token"));

        assertThrows(BadRequestException.class, () -> emailVerificationService.verifyEmail("bad-token"));
    }

    @Test
    void shouldRejectResendVerificationWhenCooldownIsActive() {
        when(mailProperties.getVerificationEmailCooldownSeconds()).thenReturn(60L);
        when(emailActionCooldownService.getRetryAfterSeconds("resend-verification", "user@l2terra.online", 60L))
                .thenReturn(18L);

        TooManyRequestsException exception = assertThrows(
                TooManyRequestsException.class,
                () -> emailVerificationService.resendVerificationEmail("user@l2terra.online")
        );

        assertTrue(exception.getCode().equals("auth.verification_email_cooldown_active"));
        verify(accountMasterRepository, never()).findByEmailIgnoreCase(any());
    }

    private AccountMaster account(Long id, String email) {
        AccountMaster accountMaster = new AccountMaster();
        accountMaster.setEmail(email);
        try {
            var idField = AccountMaster.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(accountMaster, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        return accountMaster;
    }
}

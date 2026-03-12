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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private AccountMasterRepository accountMasterRepository;

    @Mock
    private VerificationTokenService verificationTokenService;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private AsyncMailService asyncMailService;

    @Mock
    private MailProperties mailProperties;

    @Mock
    private EmailActionCooldownService emailActionCooldownService;

    @Mock
    private CurrentLanguageResolver currentLanguageResolver;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccountSessionService accountSessionService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Test
    void shouldSendResetPasswordEmailWhenAccountExists() {
        AccountMaster accountMaster = account(7L, "player@l2terra.online");
        when(mailProperties.getPasswordResetCooldownSeconds()).thenReturn(60L);
        when(emailActionCooldownService.getRetryAfterSeconds("forgot-password", "player@l2terra.online", 60L)).thenReturn(0L);
        when(accountMasterRepository.findByEmailIgnoreCase("player@l2terra.online")).thenReturn(Optional.of(accountMaster));
        when(currentLanguageResolver.resolve()).thenReturn(SupportedLanguage.US);
        when(verificationTokenService.createOrRefresh(accountMaster, AccountVerificationType.PASSWORD_RESET, 15L))
                .thenReturn("reset-token");
        when(mailProperties.getFrontendResetPasswordUrl()).thenReturn("https://l2terra.online/reset-password");
        when(emailTemplateService.buildPasswordResetMessage(any(), any(), any(), any(Long.class)))
                .thenReturn(new EmailMessage("Reset", "<html>reset</html>"));

        passwordResetService.sendResetPasswordEmailIfPossible("player@l2terra.online");

        verify(emailActionCooldownService).mark("forgot-password", "player@l2terra.online", 60L);
        verify(asyncMailService).sendHtml("player@l2terra.online", "Reset", "<html>reset</html>");
    }

    @Test
    void shouldRejectForgotPasswordWhenCooldownIsActive() {
        when(mailProperties.getPasswordResetCooldownSeconds()).thenReturn(60L);
        when(emailActionCooldownService.getRetryAfterSeconds("forgot-password", "player@l2terra.online", 60L)).thenReturn(42L);

        TooManyRequestsException exception = assertThrows(
                TooManyRequestsException.class,
                () -> passwordResetService.sendResetPasswordEmailIfPossible("player@l2terra.online")
        );

        assertEquals("auth.password_reset_cooldown_active", exception.getCode());
        assertEquals(42L, exception.getRetryAfterSeconds());
        verify(accountMasterRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void shouldResetPasswordAndRevokeSessions() {
        AccountMaster accountMaster = account(7L, "player@l2terra.online");
        accountMaster.setTokenVersion(2L);

        AccountVerification verification = new AccountVerification();
        verification.setAccount(accountMaster);
        verification.setType(AccountVerificationType.PASSWORD_RESET);
        verification.setExpiresAt(Instant.now().plusSeconds(60));

        when(verificationTokenService.getActiveVerification(
                "reset-token",
                AccountVerificationType.PASSWORD_RESET,
                "auth.invalid_password_reset_token"
        )).thenReturn(verification);
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("encoded-password");

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("reset-token");
        request.setNewPassword("NewPassword1!");

        passwordResetService.resetPassword(request);

        assertEquals("encoded-password", accountMaster.getPasswordHash());
        assertEquals(3L, accountMaster.getTokenVersion());
        verify(accountSessionService).revokeAllSessions(accountMaster);
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

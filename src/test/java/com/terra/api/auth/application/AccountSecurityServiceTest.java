package com.terra.api.auth.application;

import com.terra.api.auth.api.dto.ConfirmTwoFactorRecoveryRequest;
import com.terra.api.auth.api.dto.TwoFactorSetupVerifyRequest;
import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.domain.model.AccountTrustedDevice;
import com.terra.api.auth.domain.model.AccountVerification;
import com.terra.api.auth.domain.model.AccountVerificationType;
import com.terra.api.auth.infrastructure.config.TwoFactorRecoveryProperties;
import com.terra.api.auth.infrastructure.persistence.AccountTrustedDeviceRepository;
import com.terra.api.common.domain.exception.ResourceNotFoundException;
import com.terra.api.common.infrastructure.i18n.CurrentLanguageResolver;
import com.terra.api.mail.application.AsyncMailService;
import com.terra.api.mail.application.EmailTemplateService;
import com.terra.api.mail.domain.EmailMessage;
import com.terra.api.mail.infrastructure.config.MailProperties;
import com.terra.api.realtime.application.RealtimeSessionRevocationService;
import com.terra.api.security.application.AccountSessionService;
import com.terra.api.security.application.ClientIpResolver;
import dev.samstevens.totp.code.CodeVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountSecurityServiceTest {

    @Test
    void shouldRevokeAllTrustedDevicesAndUpsertCurrentDeviceWhenEnablingTwoFactor() {
        AuthService authService = mock(AuthService.class);
        VerificationTokenService verificationTokenService = mock(VerificationTokenService.class);
        EmailTemplateService emailTemplateService = mock(EmailTemplateService.class);
        AsyncMailService asyncMailService = mock(AsyncMailService.class);
        MailProperties mailProperties = mock(MailProperties.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CurrentLanguageResolver currentLanguageResolver = mock(CurrentLanguageResolver.class);
        AccountTrustedDeviceRepository trustedDeviceRepository = mock(AccountTrustedDeviceRepository.class);
        TwoFactorRecoveryProperties twoFactorRecoveryProperties = new TwoFactorRecoveryProperties();
        AccountSessionService accountSessionService = mock(AccountSessionService.class);
        RealtimeSessionRevocationService realtimeSessionRevocationService = mock(RealtimeSessionRevocationService.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AccountActivityService accountActivityService = mock(AccountActivityService.class);

        AccountSecurityService service = new AccountSecurityService(
                authService,
                verificationTokenService,
                emailTemplateService,
                asyncMailService,
                mailProperties,
                passwordEncoder,
                currentLanguageResolver,
                trustedDeviceRepository,
                twoFactorRecoveryProperties,
                accountSessionService,
                realtimeSessionRevocationService,
                clientIpResolver,
                accountActivityService
        );
        CodeVerifier codeVerifier = mock(CodeVerifier.class);
        ReflectionTestUtils.setField(service, "codeVerifier", codeVerifier);

        AccountMaster account = new AccountMaster();
        ReflectionTestUtils.setField(account, "id", 10L);
        account.setEmail("player@l2terra.online");
        account.setTwoFactorSecret("JBSWY3DPEHPK3PXP");

        AccountTrustedDevice previousA = new AccountTrustedDevice();
        AccountTrustedDevice previousB = new AccountTrustedDevice();
        when(authService.getCurrentUserAccount("player@l2terra.online")).thenReturn(account);
        when(trustedDeviceRepository.findByAccount_IdAndRevokedAtIsNull(10L)).thenReturn(List.of(previousA, previousB));
        when(trustedDeviceRepository.findByAccount_IdAndDeviceKeyHash(anyLong(), anyString())).thenReturn(Optional.empty());
        when(clientIpResolver.resolve(any(HttpServletRequest.class))).thenReturn("127.0.0.1");

        when(codeVerifier.isValidCode(account.getTwoFactorSecret(), "123456")).thenReturn(true);
        TwoFactorSetupVerifyRequest request = new TwoFactorSetupVerifyRequest();
        request.setCode("123456");

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("User-Agent", "JUnit");

        String trustedDeviceKey = "trusted-device-key";
        var result = service.verifyTwoFactorSetup("player@l2terra.online", request, httpRequest, trustedDeviceKey, 50L);

        assertNotNull(previousA.getRevokedAt());
        assertNotNull(previousB.getRevokedAt());
        verify(accountSessionService).revokeAllSessionsExcept(account, 50L);
        verify(trustedDeviceRepository).save(any(AccountTrustedDevice.class));
        assertNotNull(result.trustedDeviceKeyToSet());
    }

    @Test
    void shouldKeepIdempotentBehaviorWhenTwoFactorIsAlreadyEnabled() {
        AuthService authService = mock(AuthService.class);
        VerificationTokenService verificationTokenService = mock(VerificationTokenService.class);
        EmailTemplateService emailTemplateService = mock(EmailTemplateService.class);
        AsyncMailService asyncMailService = mock(AsyncMailService.class);
        MailProperties mailProperties = mock(MailProperties.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CurrentLanguageResolver currentLanguageResolver = mock(CurrentLanguageResolver.class);
        AccountTrustedDeviceRepository trustedDeviceRepository = mock(AccountTrustedDeviceRepository.class);
        TwoFactorRecoveryProperties twoFactorRecoveryProperties = new TwoFactorRecoveryProperties();
        AccountSessionService accountSessionService = mock(AccountSessionService.class);
        RealtimeSessionRevocationService realtimeSessionRevocationService = mock(RealtimeSessionRevocationService.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AccountActivityService accountActivityService = mock(AccountActivityService.class);

        AccountSecurityService service = new AccountSecurityService(
                authService,
                verificationTokenService,
                emailTemplateService,
                asyncMailService,
                mailProperties,
                passwordEncoder,
                currentLanguageResolver,
                trustedDeviceRepository,
                twoFactorRecoveryProperties,
                accountSessionService,
                realtimeSessionRevocationService,
                clientIpResolver,
                accountActivityService
        );
        CodeVerifier codeVerifier = mock(CodeVerifier.class);
        ReflectionTestUtils.setField(service, "codeVerifier", codeVerifier);

        AccountMaster account = new AccountMaster();
        ReflectionTestUtils.setField(account, "id", 10L);
        account.setEmail("player@l2terra.online");
        account.setTwoFactorSecret("JBSWY3DPEHPK3PXP");
        account.setTwoFactorEnabled(true);
        account.setTokenVersion(5L);

        when(authService.getCurrentUserAccount("player@l2terra.online")).thenReturn(account);
        when(trustedDeviceRepository.findByAccount_IdAndDeviceKeyHash(anyLong(), anyString())).thenReturn(Optional.empty());
        when(clientIpResolver.resolve(any(HttpServletRequest.class))).thenReturn("127.0.0.1");
        when(codeVerifier.isValidCode(account.getTwoFactorSecret(), "123456")).thenReturn(true);

        TwoFactorSetupVerifyRequest request = new TwoFactorSetupVerifyRequest();
        request.setCode("123456");

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("User-Agent", "JUnit");

        service.verifyTwoFactorSetup("player@l2terra.online", request, httpRequest, "trusted-device-key", 50L);

        verify(accountSessionService, never()).revokeAllSessionsExcept(any(), any());
        verify(trustedDeviceRepository).save(any(AccountTrustedDevice.class));
        verify(trustedDeviceRepository, never()).findByAccount_IdAndRevokedAtIsNull(anyLong());
        assertEquals(5L, account.getTokenVersion());
    }

    @Test
    void shouldSkipPublicRecoveryWhenCooldownWindowIsActive() {
        AuthService authService = mock(AuthService.class);
        VerificationTokenService verificationTokenService = mock(VerificationTokenService.class);
        EmailTemplateService emailTemplateService = mock(EmailTemplateService.class);
        AsyncMailService asyncMailService = mock(AsyncMailService.class);
        MailProperties mailProperties = mock(MailProperties.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CurrentLanguageResolver currentLanguageResolver = mock(CurrentLanguageResolver.class);
        AccountTrustedDeviceRepository trustedDeviceRepository = mock(AccountTrustedDeviceRepository.class);
        TwoFactorRecoveryProperties twoFactorRecoveryProperties = new TwoFactorRecoveryProperties();
        twoFactorRecoveryProperties.setRequestCooldownMinutes(2);
        AccountSessionService accountSessionService = mock(AccountSessionService.class);
        RealtimeSessionRevocationService realtimeSessionRevocationService = mock(RealtimeSessionRevocationService.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AccountActivityService accountActivityService = mock(AccountActivityService.class);

        AccountSecurityService service = new AccountSecurityService(
                authService,
                verificationTokenService,
                emailTemplateService,
                asyncMailService,
                mailProperties,
                passwordEncoder,
                currentLanguageResolver,
                trustedDeviceRepository,
                twoFactorRecoveryProperties,
                accountSessionService,
                realtimeSessionRevocationService,
                clientIpResolver,
                accountActivityService
        );

        AccountMaster account = new AccountMaster();
        account.setEmail("player@l2terra.online");
        account.setTwoFactorEnabled(true);
        account.setTwoFactorRecoveryRequestedAt(Instant.now());
        when(authService.getCurrentUserAccount("player@l2terra.online")).thenReturn(account);

        service.requestTwoFactorRecoveryIfPossible("player@l2terra.online");

        verify(verificationTokenService, never()).createOrRefresh(any(), any(AccountVerificationType.class), anyLong());
        verify(asyncMailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotPropagateTechnicalFailureInPublicRecoveryRequest() {
        AuthService authService = mock(AuthService.class);
        VerificationTokenService verificationTokenService = mock(VerificationTokenService.class);
        EmailTemplateService emailTemplateService = mock(EmailTemplateService.class);
        AsyncMailService asyncMailService = mock(AsyncMailService.class);
        MailProperties mailProperties = mock(MailProperties.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CurrentLanguageResolver currentLanguageResolver = mock(CurrentLanguageResolver.class);
        AccountTrustedDeviceRepository trustedDeviceRepository = mock(AccountTrustedDeviceRepository.class);
        TwoFactorRecoveryProperties twoFactorRecoveryProperties = new TwoFactorRecoveryProperties();
        AccountSessionService accountSessionService = mock(AccountSessionService.class);
        RealtimeSessionRevocationService realtimeSessionRevocationService = mock(RealtimeSessionRevocationService.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AccountActivityService accountActivityService = mock(AccountActivityService.class);

        AccountSecurityService service = new AccountSecurityService(
                authService,
                verificationTokenService,
                emailTemplateService,
                asyncMailService,
                mailProperties,
                passwordEncoder,
                currentLanguageResolver,
                trustedDeviceRepository,
                twoFactorRecoveryProperties,
                accountSessionService,
                realtimeSessionRevocationService,
                clientIpResolver,
                accountActivityService
        );

        AccountMaster account = new AccountMaster();
        ReflectionTestUtils.setField(account, "id", 10L);
        account.setEmail("player@l2terra.online");
        account.setTwoFactorEnabled(true);

        when(authService.getCurrentUserAccount("player@l2terra.online")).thenReturn(account);
        when(currentLanguageResolver.resolve()).thenReturn(com.terra.api.common.domain.i18n.SupportedLanguage.US);
        when(verificationTokenService.createOrRefresh(account, AccountVerificationType.TWO_FACTOR_RECOVERY, 10L)).thenReturn("token-value");
        when(mailProperties.getFrontendTwoFactorRecoveryUrl()).thenReturn("http://localhost:4200/recover-2fa");
        when(emailTemplateService.buildTwoFactorRecoveryMessage(anyString(), anyString(), any(), anyLong()))
                .thenReturn(new EmailMessage("subject", "<html/>"));
        org.mockito.Mockito.doThrow(new RuntimeException("mail down"))
                .when(asyncMailService).sendHtml(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> service.requestTwoFactorRecoveryIfPossible("player@l2terra.online"));
    }

    @Test
    void shouldSilentlyIgnoreUnknownAccountInPublicRecoveryRequest() {
        AuthService authService = mock(AuthService.class);
        VerificationTokenService verificationTokenService = mock(VerificationTokenService.class);
        EmailTemplateService emailTemplateService = mock(EmailTemplateService.class);
        AsyncMailService asyncMailService = mock(AsyncMailService.class);
        MailProperties mailProperties = mock(MailProperties.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CurrentLanguageResolver currentLanguageResolver = mock(CurrentLanguageResolver.class);
        AccountTrustedDeviceRepository trustedDeviceRepository = mock(AccountTrustedDeviceRepository.class);
        TwoFactorRecoveryProperties twoFactorRecoveryProperties = new TwoFactorRecoveryProperties();
        AccountSessionService accountSessionService = mock(AccountSessionService.class);
        RealtimeSessionRevocationService realtimeSessionRevocationService = mock(RealtimeSessionRevocationService.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AccountActivityService accountActivityService = mock(AccountActivityService.class);

        AccountSecurityService service = new AccountSecurityService(
                authService,
                verificationTokenService,
                emailTemplateService,
                asyncMailService,
                mailProperties,
                passwordEncoder,
                currentLanguageResolver,
                trustedDeviceRepository,
                twoFactorRecoveryProperties,
                accountSessionService,
                realtimeSessionRevocationService,
                clientIpResolver,
                accountActivityService
        );

        when(authService.getCurrentUserAccount("missing@l2terra.online"))
                .thenThrow(new ResourceNotFoundException("auth.user_not_found"));

        assertDoesNotThrow(() -> service.requestTwoFactorRecoveryIfPossible("missing@l2terra.online"));
        verify(asyncMailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotPropagateLookupTechnicalFailureInPublicRecoveryRequest() {
        AuthService authService = mock(AuthService.class);
        VerificationTokenService verificationTokenService = mock(VerificationTokenService.class);
        EmailTemplateService emailTemplateService = mock(EmailTemplateService.class);
        AsyncMailService asyncMailService = mock(AsyncMailService.class);
        MailProperties mailProperties = mock(MailProperties.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CurrentLanguageResolver currentLanguageResolver = mock(CurrentLanguageResolver.class);
        AccountTrustedDeviceRepository trustedDeviceRepository = mock(AccountTrustedDeviceRepository.class);
        TwoFactorRecoveryProperties twoFactorRecoveryProperties = new TwoFactorRecoveryProperties();
        AccountSessionService accountSessionService = mock(AccountSessionService.class);
        RealtimeSessionRevocationService realtimeSessionRevocationService = mock(RealtimeSessionRevocationService.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AccountActivityService accountActivityService = mock(AccountActivityService.class);

        AccountSecurityService service = new AccountSecurityService(
                authService,
                verificationTokenService,
                emailTemplateService,
                asyncMailService,
                mailProperties,
                passwordEncoder,
                currentLanguageResolver,
                trustedDeviceRepository,
                twoFactorRecoveryProperties,
                accountSessionService,
                realtimeSessionRevocationService,
                clientIpResolver,
                accountActivityService
        );

        when(authService.getCurrentUserAccount("player@l2terra.online"))
                .thenThrow(new RuntimeException("database unavailable"));

        assertDoesNotThrow(() -> service.requestTwoFactorRecoveryIfPossible("player@l2terra.online"));
        verify(asyncMailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void shouldRevokeSessionsDevicesAndRotateTokenVersionWhenConfirmingTwoFactorRecovery() {
        AuthService authService = mock(AuthService.class);
        VerificationTokenService verificationTokenService = mock(VerificationTokenService.class);
        EmailTemplateService emailTemplateService = mock(EmailTemplateService.class);
        AsyncMailService asyncMailService = mock(AsyncMailService.class);
        MailProperties mailProperties = mock(MailProperties.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CurrentLanguageResolver currentLanguageResolver = mock(CurrentLanguageResolver.class);
        AccountTrustedDeviceRepository trustedDeviceRepository = mock(AccountTrustedDeviceRepository.class);
        TwoFactorRecoveryProperties twoFactorRecoveryProperties = new TwoFactorRecoveryProperties();
        AccountSessionService accountSessionService = mock(AccountSessionService.class);
        RealtimeSessionRevocationService realtimeSessionRevocationService = mock(RealtimeSessionRevocationService.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AccountActivityService accountActivityService = mock(AccountActivityService.class);

        AccountSecurityService service = new AccountSecurityService(
                authService,
                verificationTokenService,
                emailTemplateService,
                asyncMailService,
                mailProperties,
                passwordEncoder,
                currentLanguageResolver,
                trustedDeviceRepository,
                twoFactorRecoveryProperties,
                accountSessionService,
                realtimeSessionRevocationService,
                clientIpResolver,
                accountActivityService
        );

        AccountMaster account = new AccountMaster();
        ReflectionTestUtils.setField(account, "id", 10L);
        account.setPasswordHash("encoded-password");
        account.setTwoFactorEnabled(true);
        account.setTwoFactorSecret("JBSWY3DPEHPK3PXP");
        account.setTwoFactorEnabledAt(Instant.now());
        account.setTwoFactorRecoveryRequestedAt(Instant.now());
        account.setTokenVersion(7L);

        AccountVerification verification = new AccountVerification();
        verification.setAccount(account);
        AccountTrustedDevice trustedDevice = new AccountTrustedDevice();

        when(verificationTokenService.getActiveVerification(
                "recovery-token",
                AccountVerificationType.TWO_FACTOR_RECOVERY,
                "auth.invalid_two_factor_recovery_token"
        )).thenReturn(verification);
        when(passwordEncoder.matches("current-password", "encoded-password")).thenReturn(true);
        when(trustedDeviceRepository.findByAccount_IdAndRevokedAtIsNull(10L)).thenReturn(List.of(trustedDevice));

        ConfirmTwoFactorRecoveryRequest request = new ConfirmTwoFactorRecoveryRequest();
        request.setToken("recovery-token");
        request.setCurrentPassword("current-password");

        service.confirmTwoFactorRecovery(request);

        assertFalse(account.isTwoFactorEnabled());
        assertEquals(8L, account.getTokenVersion());
        assertNotNull(verification.getUsedAt());
        assertNotNull(trustedDevice.getRevokedAt());
        verify(accountSessionService).revokeAllSessions(account);
        verify(realtimeSessionRevocationService).revokeAccountSessions(10L, "two_factor_recovery");
    }
}

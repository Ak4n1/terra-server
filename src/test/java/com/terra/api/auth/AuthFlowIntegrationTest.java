package com.terra.api.auth;

import com.terra.api.auth.entity.AccountSession;
import com.terra.api.auth.repository.AccountMasterRepository;
import com.terra.api.auth.repository.AccountSessionRepository;
import com.terra.api.auth.repository.AccountVerificationRepository;
import com.terra.api.mail.service.MailSenderService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(AuthFlowIntegrationTest.TestMailConfig.class)
class AuthFlowIntegrationTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\?token=([^\"']+)");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AccountMasterRepository accountMasterRepository;

    @Autowired
    private AccountSessionRepository accountSessionRepository;

    @Autowired
    private AccountVerificationRepository accountVerificationRepository;

    @Autowired
    private CapturingMailSenderService capturingMailSenderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        accountSessionRepository.deleteAll();
        accountVerificationRepository.deleteAll();
        accountMasterRepository.deleteAll();
        capturingMailSenderService.clear();
    }

    @Test
    void registerShouldCreateUserWithUserRole() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("register-player1"));
                            return request;
                        })
                        .content("""
                                {
                                  "email": "player1@l2terra.online",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("auth.verification_email_sent"))
                .andExpect(jsonPath("$.data.email").value("player1@l2terra.online"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"))
                .andExpect(jsonPath("$.data.emailVerified").value(false));
    }

    @Test
    void loginShouldSetAuthAndCsrfCookies() throws Exception {
        registerAndVerify("player2@l2terra.online");

        MvcResult loginResult = login("player2@l2terra.online");

        ParsedCookie accessCookie = getSetCookie(loginResult, "terra_access_token");
        ParsedCookie refreshCookie = getSetCookie(loginResult, "terra_refresh_token");
        ParsedCookie csrfCookie = getSetCookie(loginResult, "XSRF-TOKEN");

        assertNotNull(accessCookie.value());
        assertNotNull(refreshCookie.value());
        assertNotNull(csrfCookie.value());
        assertFalse(accessCookie.value().isBlank());
        assertFalse(refreshCookie.value().isBlank());
        assertFalse(csrfCookie.value().isBlank());
        assertEquals(1, accountSessionRepository.count());
    }

    @Test
    void meShouldReturnCurrentUserWhenAccessCookieIsPresent() throws Exception {
        registerAndVerify("player3@l2terra.online");
        MvcResult loginResult = login("player3@l2terra.online");

        Cookie accessCookie = new Cookie("terra_access_token", getSetCookie(loginResult, "terra_access_token").value());

        mockMvc.perform(get("/api/auth/me").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("player3@l2terra.online"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }

    @Test
    void refreshShouldRequireCsrfTokenAndRotateSession() throws Exception {
        registerAndVerify("player4@l2terra.online");
        MvcResult loginResult = login("player4@l2terra.online");

        Cookie refreshCookie = new Cookie("terra_refresh_token", getSetCookie(loginResult, "terra_refresh_token").value());
        Cookie csrfCookie = new Cookie("XSRF-TOKEN", getSetCookie(loginResult, "XSRF-TOKEN").value());
        String originalRefreshValue = refreshCookie.getValue();

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_csrf_token"));

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie, csrfCookie)
                        .header("X-CSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("auth.token_refreshed"))
                .andReturn();

        ParsedCookie rotatedRefreshCookie = getSetCookie(refreshResult, "terra_refresh_token");
        ParsedCookie rotatedAccessCookie = getSetCookie(refreshResult, "terra_access_token");
        ParsedCookie rotatedCsrfCookie = getSetCookie(refreshResult, "XSRF-TOKEN");

        assertNotEquals(originalRefreshValue, rotatedRefreshCookie.value());
        assertFalse(rotatedAccessCookie.value().isBlank());
        assertFalse(rotatedCsrfCookie.value().isBlank());

        List<AccountSession> sessions = accountSessionRepository.findAll();
        assertEquals(2, sessions.size());
        assertEquals(1, sessions.stream().filter(session -> session.getRevokedAt() != null).count());
    }

    @Test
    void logoutShouldRevokeSessionAndClearCookies() throws Exception {
        registerAndVerify("player5@l2terra.online");
        MvcResult loginResult = login("player5@l2terra.online");

        Cookie refreshCookie = new Cookie("terra_refresh_token", getSetCookie(loginResult, "terra_refresh_token").value());
        Cookie accessCookie = new Cookie("terra_access_token", getSetCookie(loginResult, "terra_access_token").value());
        Cookie csrfCookie = new Cookie("XSRF-TOKEN", getSetCookie(loginResult, "XSRF-TOKEN").value());

        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .cookie(accessCookie, refreshCookie, csrfCookie)
                        .header("X-CSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("auth.logout_success"))
                .andReturn();

        assertEquals(0L, getSetCookie(logoutResult, "terra_access_token").maxAge());
        assertEquals(0L, getSetCookie(logoutResult, "terra_refresh_token").maxAge());
        assertEquals(0L, getSetCookie(logoutResult, "XSRF-TOKEN").maxAge());

        AccountSession session = accountSessionRepository.findAll().get(0);
        assertNotNull(session.getRevokedAt());
    }

    @Test
    void logoutAllShouldRevokeAllSessionsAndInvalidateCurrentAccessTokenImmediately() throws Exception {
        registerAndVerify("player7@l2terra.online");
        MvcResult firstLogin = login("player7@l2terra.online");
        MvcResult secondLogin = login("player7@l2terra.online");

        Cookie secondAccessCookie = new Cookie("terra_access_token", getSetCookie(secondLogin, "terra_access_token").value());
        Cookie secondRefreshCookie = new Cookie("terra_refresh_token", getSetCookie(secondLogin, "terra_refresh_token").value());
        Cookie secondCsrfCookie = new Cookie("XSRF-TOKEN", getSetCookie(secondLogin, "XSRF-TOKEN").value());

        mockMvc.perform(post("/api/auth/logout-all")
                        .cookie(secondAccessCookie, secondRefreshCookie, secondCsrfCookie)
                        .header("X-CSRF-TOKEN", secondCsrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("auth.logout_all_success"));

        Cookie firstAccessCookie = new Cookie("terra_access_token", getSetCookie(firstLogin, "terra_access_token").value());
        Cookie firstRefreshCookie = new Cookie("terra_refresh_token", getSetCookie(firstLogin, "terra_refresh_token").value());
        Cookie firstCsrfCookie = new Cookie("XSRF-TOKEN", getSetCookie(firstLogin, "XSRF-TOKEN").value());

        mockMvc.perform(get("/api/auth/me").cookie(firstAccessCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthorized"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(firstRefreshCookie, firstCsrfCookie)
                        .header("X-CSRF-TOKEN", firstCsrfCookie.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_refresh_token"));

        List<AccountSession> sessions = accountSessionRepository.findAll();
        assertEquals(2, sessions.size());
        assertEquals(2, sessions.stream().filter(session -> session.getRevokedAt() != null).count());
    }

    @Test
    void loginShouldBeRateLimitedAfterConfiguredAttempts() throws Exception {
        registerAndVerify("player6@l2terra.online");

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "player6@l2terra.online",
                                      "password": "wrong-password"
                                    }
                                    """)
                            .with(request -> {
                                request.setRemoteAddr("192.168.1.10");
                                return request;
                            }))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "player6@l2terra.online",
                                  "password": "wrong-password"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("192.168.1.10");
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limit.exceeded"));
    }

    @Test
    void loginShouldRejectUserWhenEmailIsNotVerified() throws Exception {
        register("player-unverified@l2terra.online");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "player-unverified@l2terra.online",
                                  "password": "Password1!"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("login-player-unverified"));
                            return request;
                        }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.email_not_verified"));
    }

    @Test
    void verifyEmailShouldActivateAccountAndAllowLogin() throws Exception {
        register("player-verify@l2terra.online");
        String token = extractLatestVerificationToken();

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(token))
                        .with(request -> {
                            request.setRemoteAddr(ipFor("verify-email-player-verify"));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("auth.email_verified"));

        login("player-verify@l2terra.online");
    }

    @Test
    void resendVerificationShouldReplacePreviousToken() throws Exception {
        register("player-resend@l2terra.online");
        String firstToken = extractLatestVerificationToken();

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "player-resend@l2terra.online"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("resend-player-resend"));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("auth.verification_email_resent_if_possible"));

        String secondToken = extractLatestVerificationToken();
        org.junit.jupiter.api.Assertions.assertNotEquals(firstToken, secondToken);

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(firstToken))
                        .with(request -> {
                            request.setRemoteAddr(ipFor("verify-old-player-resend"));
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.invalid_verification_token"));

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(secondToken))
                        .with(request -> {
                            request.setRemoteAddr(ipFor("verify-new-player-resend"));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("auth.email_verified"));
    }

    @Test
    void resendVerificationShouldStayGenericForUnknownAccount() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "unknown@l2terra.online"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("resend-unknown"));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("auth.verification_email_resent_if_possible"));
    }

    @Test
    void resendVerificationShouldReturnCooldownAndRetryAfterWhenRepeatedForSameEmail() throws Exception {
        register("player-resend-cooldown@l2terra.online");

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "player-resend-cooldown@l2terra.online"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("resend-cooldown-first"));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("auth.verification_email_resent_if_possible"));

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "player-resend-cooldown@l2terra.online"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("resend-cooldown-second"));
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("auth.verification_email_cooldown_active"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    @Test
    void forgotPasswordShouldStayGenericForUnknownAccount() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "unknown@l2terra.online"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("forgot-unknown"));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("auth.password_reset_email_sent_if_possible"));
    }

    @Test
    void forgotPasswordShouldReturnCooldownAndRetryAfterWhenRepeatedForSameEmail() throws Exception {
        registerAndVerify("player-reset-cooldown@l2terra.online");

        requestPasswordReset("player-reset-cooldown@l2terra.online", ipFor("forgot-reset-cooldown-first"));

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "player-reset-cooldown@l2terra.online"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("forgot-reset-cooldown-second"));
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("auth.password_reset_cooldown_active"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    @Test
    void forgotPasswordShouldSendResetEmailAndAllowPasswordChange() throws Exception {
        registerAndVerify("player-reset@l2terra.online");

        requestPasswordReset("player-reset@l2terra.online", ipFor("forgot-player-reset"));
        String resetHtmlBody = capturingMailSenderService.lastBody();
        assertTrue(resetHtmlBody.contains("Reset Password"));
        assertTrue(resetHtmlBody.contains("background-color: rgba(14, 14, 14, 0.9);"));
        assertTrue(resetHtmlBody.contains("border: 1px solid #b56d19;"));

        String resetToken = extractLatestVerificationToken();

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "NewPassword1!"
                                }
                                """.formatted(resetToken))
                        .with(request -> {
                            request.setRemoteAddr(ipFor("reset-player-reset"));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("auth.password_reset_success"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "player-reset@l2terra.online",
                                  "password": "Password1!"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("login-old-player-reset"));
                            return request;
                        }))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "player-reset@l2terra.online",
                                  "password": "NewPassword1!"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("login-new-player-reset"));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("auth.login_success"));
    }

    @Test
    void resetPasswordShouldRevokeExistingSessionsImmediately() throws Exception {
        registerAndVerify("player-reset-session@l2terra.online");
        MvcResult loginResult = login("player-reset-session@l2terra.online");

        Cookie accessCookie = new Cookie("terra_access_token", getSetCookie(loginResult, "terra_access_token").value());
        Cookie refreshCookie = new Cookie("terra_refresh_token", getSetCookie(loginResult, "terra_refresh_token").value());
        Cookie csrfCookie = new Cookie("XSRF-TOKEN", getSetCookie(loginResult, "XSRF-TOKEN").value());

        requestPasswordReset("player-reset-session@l2terra.online", ipFor("forgot-player-reset-session"));
        String resetToken = extractLatestVerificationToken();

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "AnotherPass1!"
                                }
                                """.formatted(resetToken))
                        .with(request -> {
                            request.setRemoteAddr(ipFor("reset-player-reset-session"));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("auth.password_reset_success"));

        mockMvc.perform(get("/api/auth/me").cookie(accessCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthorized"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie, csrfCookie)
                        .header("X-CSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_refresh_token"));
    }

    @Test
    void registerShouldReturnSpanishValidationMessagesWhenRequested() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Language", "es")
                        .with(request -> {
                            request.setRemoteAddr(ipFor("register-invalid-es"));
                            return request;
                        })
                        .content("""
                                {
                                  "email": "bad-email",
                                  "password": "weakweak"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.message").value("La validacion fallo"))
                .andExpect(jsonPath("$.errors.email").value("El formato del email es invalido"))
                .andExpect(jsonPath("$.errors.password").value("La contraseña debe tener al menos 8 caracteres, 1 mayuscula, 1 numero y 1 caracter especial"));
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("register-" + email));
                            return request;
                        })
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());

        String verificationHtmlBody = capturingMailSenderService.lastBody();
        assertTrue(verificationHtmlBody.contains("Verify Email"));
        assertTrue(verificationHtmlBody.contains("background-color: rgba(14, 14, 14, 0.9);"));
    }

    private void registerAndVerify(String email) throws Exception {
        register(email);
        verifyLatestEmail(ipFor("verify-" + email));
    }

    private void requestPasswordReset(String email, String remoteAddress) throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s"
                                }
                                """.formatted(email))
                        .with(request -> {
                            request.setRemoteAddr(remoteAddress);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("auth.password_reset_email_sent_if_possible"));
    }

    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!"
                                }
                                """.formatted(email))
                        .with(request -> {
                            request.setRemoteAddr(ipFor("login-" + email));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("auth.login_success"))
                .andReturn();
    }

    private ParsedCookie getSetCookie(MvcResult result, String cookieName) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .map(this::parseSetCookie)
                .filter(cookie -> cookieName.equals(cookie.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cookie not found: " + cookieName));
    }

    private ParsedCookie parseSetCookie(String headerValue) {
        String[] parts = headerValue.split(";\\s*");
        String[] nameAndValue = parts[0].split("=", 2);
        long maxAge = -1;

        for (int i = 1; i < parts.length; i++) {
            if (parts[i].startsWith("Max-Age=")) {
                maxAge = Long.parseLong(parts[i].substring("Max-Age=".length()));
                break;
            }
        }

        return new ParsedCookie(nameAndValue[0], nameAndValue.length > 1 ? nameAndValue[1] : "", maxAge);
    }

    private String ipFor(String seed) {
        int hash = Math.abs(seed.hashCode());
        int second = (hash % 200) + 1;
        int third = ((hash / 200) % 200) + 1;
        int fourth = ((hash / 40000) % 200) + 1;
        return "10.%d.%d.%d".formatted(second, third, fourth);
    }

    private void verifyLatestEmail(String remoteAddress) throws Exception {
        String token = extractLatestVerificationToken();
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(token))
                        .with(request -> {
                            request.setRemoteAddr(remoteAddress);
                            return request;
                        }))
                .andExpect(status().isOk());
    }

    private String extractLatestVerificationToken() {
        String htmlBody = capturingMailSenderService.lastBody();
        Matcher matcher = TOKEN_PATTERN.matcher(htmlBody);
        if (!matcher.find()) {
            throw new IllegalStateException("Verification token not found in email body");
        }
        return matcher.group(1);
    }

    private record ParsedCookie(String name, String value, long maxAge) {
    }

    @TestConfiguration
    static class TestMailConfig {

        @Bean
        @Primary
        CapturingMailSenderService mailSenderService() {
            return new CapturingMailSenderService();
        }
    }

    static class CapturingMailSenderService implements MailSenderService {

        private final AtomicReference<String> lastBody = new AtomicReference<>();

        @Override
        public void sendHtml(String to, String subject, String htmlBody) {
            lastBody.set(htmlBody);
        }

        String lastBody() {
            String body = lastBody.get();
            if (body == null) {
                throw new IllegalStateException("No email captured");
            }
            return body;
        }

        void clear() {
            lastBody.set(null);
        }
    }
}

package com.terra.api.auth.api.controller;

import com.terra.api.auth.api.dto.AuthSessionResponse;
import com.terra.api.auth.api.dto.AuthClientConfigResponse;
import com.terra.api.auth.application.AuthLoginResult;
import com.terra.api.auth.api.dto.ConfirmTwoFactorRecoveryRequest;
import com.terra.api.auth.api.dto.ForgotPasswordRequest;
import com.terra.api.auth.api.dto.LoginRequest;
import com.terra.api.auth.api.dto.ResendVerificationRequest;
import com.terra.api.auth.api.dto.RefreshSessionResponse;
import com.terra.api.auth.api.dto.RegisterRequest;
import com.terra.api.auth.api.dto.ResetPasswordRequest;
import com.terra.api.auth.api.dto.TwoFactorRecoveryRequest;
import com.terra.api.auth.api.dto.UpdatePreferredLanguageRequest;
import com.terra.api.auth.api.dto.UserResponse;
import com.terra.api.auth.api.dto.VerifyEmailRequest;
import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.domain.model.AccountSession;
import com.terra.api.auth.application.AuthService;
import com.terra.api.auth.application.AccountSecurityService;
import com.terra.api.auth.application.EmailVerificationService;
import com.terra.api.auth.application.PasswordResetService;
import com.terra.api.common.domain.exception.ResourceConflictException;
import com.terra.api.common.domain.exception.ResourceNotFoundException;
import com.terra.api.common.infrastructure.i18n.MessageResolver;
import com.terra.api.common.infrastructure.web.ApiResponse;
import com.terra.api.idempotency.application.IdempotencyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import com.terra.api.security.infrastructure.config.JwtProperties;
import com.terra.api.security.infrastructure.config.CsrfProperties;
import com.terra.api.security.domain.JwtAuthenticationException;
import com.terra.api.security.infrastructure.jwt.JwtService;
import com.terra.api.security.domain.JwtTokenType;
import com.terra.api.realtime.application.RealtimeSessionRevocationService;
import com.terra.api.security.application.AccountSessionService;
import com.terra.api.security.infrastructure.web.CsrfCookieService;
import com.terra.api.security.infrastructure.web.JwtCookieService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String TRUSTED_DEVICE_COOKIE_NAME = "terra_trusted_device";
    private static final Duration TRUSTED_DEVICE_COOKIE_MAX_AGE = Duration.ofDays(30);

    private final AuthService authService;
    private final MessageResolver messageResolver;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;
    private final JwtProperties jwtProperties;
    private final CsrfProperties csrfProperties;
    private final AccountSessionService accountSessionService;
    private final CsrfCookieService csrfCookieService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final AccountSecurityService accountSecurityService;
    private final RealtimeSessionRevocationService realtimeSessionRevocationService;
    private final IdempotencyService idempotencyService;

    public AuthController(AuthService authService,
                          MessageResolver messageResolver,
                          JwtService jwtService,
                          JwtCookieService jwtCookieService,
                          JwtProperties jwtProperties,
                          CsrfProperties csrfProperties,
                          AccountSessionService accountSessionService,
                          CsrfCookieService csrfCookieService,
                          EmailVerificationService emailVerificationService,
                          PasswordResetService passwordResetService,
                          AccountSecurityService accountSecurityService,
                          RealtimeSessionRevocationService realtimeSessionRevocationService,
                          IdempotencyService idempotencyService) {
        this.authService = authService;
        this.messageResolver = messageResolver;
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
        this.jwtProperties = jwtProperties;
        this.csrfProperties = csrfProperties;
        this.accountSessionService = accountSessionService;
        this.csrfCookieService = csrfCookieService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.accountSecurityService = accountSecurityService;
        this.realtimeSessionRevocationService = realtimeSessionRevocationService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("auth.verification_email_sent", messageResolver.get("auth.verification_email_sent"), user));
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<AuthClientConfigResponse>> config() {
        HttpHeaders headers = new HttpHeaders();
        csrfCookieService.addCookie(headers, csrfCookieService.generateToken());
        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.of(
                        "auth.client_config",
                        "Auth client config",
                        new AuthClientConfigResponse(csrfProperties.getCookieName(), csrfProperties.getHeaderName())
                ));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verifyEmail(request.getToken());
        return ResponseEntity.ok(ApiResponse.of("auth.email_verified", messageResolver.get("auth.email_verified")));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        try {
            emailVerificationService.resendVerificationEmail(request.getEmail());
        } catch (ResourceNotFoundException | ResourceConflictException ignored) {
            // Keep the response generic to reduce account enumeration through resend.
        }

        return ResponseEntity.ok(ApiResponse.of(
                "auth.verification_email_resent_if_possible",
                messageResolver.get("auth.verification_email_resent_if_possible")
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.sendResetPasswordEmailIfPossible(request.getEmail());
        return ResponseEntity.ok(ApiResponse.of(
                "auth.password_reset_email_sent_if_possible",
                messageResolver.get("auth.password_reset_email_sent_if_possible")
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        HttpHeaders headers = new HttpHeaders();
        jwtCookieService.clearAuthenticationCookies(headers);
        csrfCookieService.clearCookie(headers);
        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.of(
                        "auth.password_reset_success",
                        messageResolver.get("auth.password_reset_success")
                ));
    }

    @PostMapping("/2fa/recovery/request")
    public ResponseEntity<ApiResponse<Void>> requestTwoFactorRecovery(@Valid @RequestBody TwoFactorRecoveryRequest request) {
        accountSecurityService.requestTwoFactorRecoveryIfPossible(request.getEmail());
        return ResponseEntity.ok(ApiResponse.of(
                "auth.two_factor_recovery_email_sent",
                messageResolver.get("auth.two_factor_recovery_email_sent")
        ));
    }

    @PostMapping("/2fa/recovery/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmTwoFactorRecovery(@Valid @RequestBody ConfirmTwoFactorRecoveryRequest request) {
        accountSecurityService.confirmTwoFactorRecovery(request);
        return ResponseEntity.ok(ApiResponse.of(
                "auth.two_factor_recovery_applied",
                messageResolver.get("auth.two_factor_recovery_applied")
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthSessionResponse>> login(@Valid @RequestBody LoginRequest request,
                                                                  HttpServletRequest httpServletRequest) {
        String trustedDeviceKey = extractCookieValue(httpServletRequest, TRUSTED_DEVICE_COOKIE_NAME);
        AuthLoginResult loginResult = authService.authenticate(request, httpServletRequest, trustedDeviceKey);
        AccountMaster accountMaster = loginResult.account();
        UserResponse user = authService.getCurrentUser(accountMaster.getEmail());

        HttpHeaders headers = new HttpHeaders();
        issueSession(headers, accountMaster, null, httpServletRequest);
        if (loginResult.trustedDeviceKeyToSet() != null && !loginResult.trustedDeviceKeyToSet().isBlank()) {
            addTrustedDeviceCookie(headers, loginResult.trustedDeviceKeyToSet());
        }
        csrfCookieService.addCookie(headers, csrfCookieService.generateToken());

        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.of("auth.login_success", messageResolver.get("auth.login_success"), new AuthSessionResponse(user)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshSessionResponse>> refresh(HttpServletRequest request,
                                                                       @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String refreshToken = extractCookieValue(request, jwtProperties.getRefreshCookieName());
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new JwtAuthenticationException("auth.invalid_refresh_token");
        }

        Long accountId = jwtService.extractAccountId(refreshToken, JwtTokenType.REFRESH);
        String requestHash = idempotencyService.hash(
                "auth.refresh",
                Map.of("accountId", accountId)
        );

        return idempotencyService.execute(
                "auth.refresh",
                idempotencyKey,
                requestHash,
                () -> refreshInternal(request, refreshToken)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String refreshToken = extractCookieValue(request, jwtProperties.getRefreshCookieName());
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                AccountSession accountSession = accountSessionService.getActiveSession(refreshToken);
                accountSessionService.revokeSession(refreshToken);
                realtimeSessionRevocationService.revokeAccountSession(
                        accountSession.getId(),
                        accountSession.getAccount().getId(),
                        "logout"
                );
            } catch (JwtAuthenticationException ignored) {
                accountSessionService.revokeSession(refreshToken);
            }
        }

        HttpHeaders headers = new HttpHeaders();
        jwtCookieService.clearAuthenticationCookies(headers);
        csrfCookieService.clearCookie(headers);

        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.of("auth.logout_success", messageResolver.get("auth.logout_success")));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(Authentication authentication) {
        authService.revokeAllSessions(authentication.getName());

        HttpHeaders headers = new HttpHeaders();
        jwtCookieService.clearAuthenticationCookies(headers);
        clearTrustedDeviceCookie(headers);
        csrfCookieService.clearCookie(headers);

        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.of("auth.logout_all_success", messageResolver.get("auth.logout_all_success")));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(Authentication authentication) {
        UserResponse user = authService.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.of("auth.current_user", messageResolver.get("auth.current_user"), user));
    }

    @PatchMapping("/preferred-language")
    public ResponseEntity<ApiResponse<UserResponse>> updatePreferredLanguage(Authentication authentication,
                                                                             @RequestBody UpdatePreferredLanguageRequest request) {
        UserResponse user = authService.updatePreferredLanguage(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.of(
                "auth.preferred_language_updated",
                messageResolver.get("auth.preferred_language_updated"),
                user
        ));
    }

    private String extractCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void issueSession(HttpHeaders headers,
                              AccountMaster accountMaster,
                              String currentRefreshToken,
                              HttpServletRequest request) {
        String accessToken = jwtService.generateAccessToken(accountMaster);
        String refreshToken = jwtService.generateRefreshToken(accountMaster);

        jwtCookieService.addAccessTokenCookie(headers, accessToken);
        jwtCookieService.addRefreshTokenCookie(headers, refreshToken);

        if (currentRefreshToken == null) {
            accountSessionService.createSession(
                    accountMaster,
                    refreshToken,
                    jwtService.extractExpiration(refreshToken, JwtTokenType.REFRESH),
                    request
            );
            return;
        }

        accountSessionService.rotateSession(
                accountMaster,
                currentRefreshToken,
                refreshToken,
                jwtService.extractExpiration(refreshToken, JwtTokenType.REFRESH),
                request
        );
    }

    private void addTrustedDeviceCookie(HttpHeaders headers, String value) {
        headers.add(HttpHeaders.SET_COOKIE, ResponseCookie.from(TRUSTED_DEVICE_COOKIE_NAME, value)
                .httpOnly(jwtProperties.getCookie().isHttpOnly())
                .secure(jwtProperties.getCookie().isSecure())
                .sameSite(jwtProperties.getCookie().getSameSite())
                .path(jwtProperties.getCookie().getPath())
                .maxAge(TRUSTED_DEVICE_COOKIE_MAX_AGE)
                .build().toString());
    }

    private void clearTrustedDeviceCookie(HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, ResponseCookie.from(TRUSTED_DEVICE_COOKIE_NAME, "")
                .httpOnly(jwtProperties.getCookie().isHttpOnly())
                .secure(jwtProperties.getCookie().isSecure())
                .sameSite(jwtProperties.getCookie().getSameSite())
                .path(jwtProperties.getCookie().getPath())
                .maxAge(Duration.ZERO)
                .build().toString());
    }

    private ResponseEntity<ApiResponse<RefreshSessionResponse>> refreshInternal(HttpServletRequest request, String refreshToken) {
        accountSessionService.getActiveSession(refreshToken);
        Long accountId = jwtService.extractAccountId(refreshToken, JwtTokenType.REFRESH);
        AccountMaster accountMaster = authService.getCurrentUserAccount(accountId);
        if (jwtService.extractTokenVersion(refreshToken, JwtTokenType.REFRESH) != accountMaster.getTokenVersion()) {
            throw new JwtAuthenticationException("auth.invalid_refresh_token");
        }

        HttpHeaders headers = new HttpHeaders();
        issueSession(headers, accountMaster, refreshToken, request);
        csrfCookieService.addCookie(headers, csrfCookieService.generateToken());

        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.of(
                        "auth.token_refreshed",
                        messageResolver.get("auth.token_refreshed"),
                        new RefreshSessionResponse("refreshed")
                ));
    }
}

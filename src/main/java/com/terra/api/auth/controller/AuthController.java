package com.terra.api.auth.controller;

import com.terra.api.auth.dto.AuthSessionResponse;
import com.terra.api.auth.dto.AuthClientConfigResponse;
import com.terra.api.auth.dto.ForgotPasswordRequest;
import com.terra.api.auth.dto.LoginRequest;
import com.terra.api.auth.dto.ResendVerificationRequest;
import com.terra.api.auth.dto.RefreshSessionResponse;
import com.terra.api.auth.dto.RegisterRequest;
import com.terra.api.auth.dto.ResetPasswordRequest;
import com.terra.api.auth.dto.UserResponse;
import com.terra.api.auth.dto.VerifyEmailRequest;
import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.service.AuthService;
import com.terra.api.auth.service.EmailVerificationService;
import com.terra.api.auth.service.PasswordResetService;
import com.terra.api.common.exception.ResourceConflictException;
import com.terra.api.common.exception.ResourceNotFoundException;
import com.terra.api.common.i18n.message.MessageResolver;
import com.terra.api.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import com.terra.api.security.config.JwtProperties;
import com.terra.api.security.config.CsrfProperties;
import com.terra.api.security.jwt.JwtAuthenticationException;
import com.terra.api.security.jwt.JwtService;
import com.terra.api.security.jwt.JwtTokenType;
import com.terra.api.security.service.AccountSessionService;
import com.terra.api.security.service.CsrfCookieService;
import com.terra.api.security.service.JwtCookieService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

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

    public AuthController(AuthService authService,
                          MessageResolver messageResolver,
                          JwtService jwtService,
                          JwtCookieService jwtCookieService,
                          JwtProperties jwtProperties,
                          CsrfProperties csrfProperties,
                          AccountSessionService accountSessionService,
                          CsrfCookieService csrfCookieService,
                          EmailVerificationService emailVerificationService,
                          PasswordResetService passwordResetService) {
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
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("auth.verification_email_sent", messageResolver.get("auth.verification_email_sent"), user));
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<AuthClientConfigResponse>> config() {
        return ResponseEntity.ok(ApiResponse.of(
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
        return ResponseEntity.ok(ApiResponse.of(
                "auth.password_reset_success",
                messageResolver.get("auth.password_reset_success")
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthSessionResponse>> login(@Valid @RequestBody LoginRequest request,
                                                                  HttpServletRequest httpServletRequest) {
        AccountMaster accountMaster = authService.authenticate(request);
        UserResponse user = authService.getCurrentUser(accountMaster.getEmail());

        HttpHeaders headers = new HttpHeaders();
        issueSession(headers, accountMaster, null, httpServletRequest);
        csrfCookieService.addCookie(headers, csrfCookieService.generateToken());

        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.of("auth.login_success", messageResolver.get("auth.login_success"), new AuthSessionResponse(user)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshSessionResponse>> refresh(HttpServletRequest request) {
        String refreshToken = extractCookieValue(request, jwtProperties.getRefreshCookieName());
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new JwtAuthenticationException("auth.invalid_refresh_token");
        }

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

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String refreshToken = extractCookieValue(request, jwtProperties.getRefreshCookieName());
        if (refreshToken != null && !refreshToken.isBlank()) {
            accountSessionService.revokeSession(refreshToken);
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
}

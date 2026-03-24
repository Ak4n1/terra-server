package com.terra.api.auth.api.controller;

import com.terra.api.auth.api.dto.AccountSecurityStatusResponse;
import com.terra.api.auth.api.dto.ChangeAccountPasswordRequest;
import com.terra.api.auth.api.dto.ConfirmTwoFactorRecoveryRequest;
import com.terra.api.auth.api.dto.TrustedDeviceResponse;
import com.terra.api.auth.api.dto.TwoFactorSetupInitResponse;
import com.terra.api.auth.api.dto.TwoFactorSetupVerifyRequest;
import com.terra.api.auth.application.AccountPasswordSecurityService;
import com.terra.api.auth.application.AccountSecurityService;
import com.terra.api.auth.application.TwoFactorSetupVerificationResult;
import com.terra.api.common.infrastructure.i18n.MessageResolver;
import com.terra.api.common.infrastructure.web.ApiResponse;
import com.terra.api.idempotency.application.IdempotencyService;
import com.terra.api.security.application.AccountSessionService;
import com.terra.api.security.infrastructure.config.JwtProperties;
import com.terra.api.security.infrastructure.jwt.JwtService;
import com.terra.api.security.domain.JwtTokenType;
import com.terra.api.security.infrastructure.web.JwtCookieService;
import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/account/settings/security")
public class AccountSettingsSecurityController {
    private static final String TRUSTED_DEVICE_COOKIE_NAME = "terra_trusted_device";
    private static final Duration TRUSTED_DEVICE_COOKIE_MAX_AGE = Duration.ofDays(30);

    private final AccountSecurityService accountSecurityService;
    private final AccountPasswordSecurityService accountPasswordSecurityService;
    private final MessageResolver messageResolver;
    private final IdempotencyService idempotencyService;
    private final JwtProperties jwtProperties;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;
    private final AccountSessionService accountSessionService;

    public AccountSettingsSecurityController(AccountSecurityService accountSecurityService,
                                             AccountPasswordSecurityService accountPasswordSecurityService,
                                             MessageResolver messageResolver,
                                             IdempotencyService idempotencyService,
                                             JwtProperties jwtProperties,
                                             JwtService jwtService,
                                             JwtCookieService jwtCookieService,
                                             AccountSessionService accountSessionService) {
        this.accountSecurityService = accountSecurityService;
        this.accountPasswordSecurityService = accountPasswordSecurityService;
        this.messageResolver = messageResolver;
        this.idempotencyService = idempotencyService;
        this.jwtProperties = jwtProperties;
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
        this.accountSessionService = accountSessionService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<AccountSecurityStatusResponse>> getStatus(Authentication authentication) {
        AccountSecurityStatusResponse status = accountSecurityService.getStatus(authentication.getName());
        return ResponseEntity.ok(ApiResponse.of(
                "auth.security_status_loaded",
                messageResolver.get("auth.security_status_loaded"),
                status
        ));
    }

    @GetMapping("/trusted-devices")
    public ResponseEntity<ApiResponse<List<TrustedDeviceResponse>>> getTrustedDevices(Authentication authentication,
                                                                                      HttpServletRequest request) {
        List<TrustedDeviceResponse> trustedDevices = accountSecurityService.listTrustedDevices(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.of(
                "auth.trusted_devices_loaded",
                messageResolver.get("auth.trusted_devices_loaded"),
                trustedDevices
        ));
    }

    @PostMapping("/2fa/setup/init")
    public ResponseEntity<ApiResponse<TwoFactorSetupInitResponse>> initTwoFactorSetup(Authentication authentication) {
        TwoFactorSetupInitResponse response = accountSecurityService.initTwoFactorSetup(authentication.getName());
        return ResponseEntity.ok(ApiResponse.of(
                "auth.two_factor_setup_initialized",
                messageResolver.get("auth.two_factor_setup_initialized"),
                response
        ));
    }

    @PostMapping("/2fa/setup/verify")
    public ResponseEntity<ApiResponse<Void>> verifyTwoFactorSetup(Authentication authentication,
                                                                  @Valid @RequestBody TwoFactorSetupVerifyRequest request,
                                                                  HttpServletRequest httpServletRequest) {
        String trustedDeviceKey = extractCookieValue(httpServletRequest, TRUSTED_DEVICE_COOKIE_NAME);
        String currentRefreshToken = extractCookieValue(httpServletRequest, jwtProperties.getRefreshCookieName());
        Long keepSessionId = resolveSessionId(currentRefreshToken);

        TwoFactorSetupVerificationResult verificationResult = accountSecurityService.verifyTwoFactorSetup(
                authentication.getName(),
                request,
                httpServletRequest,
                trustedDeviceKey,
                keepSessionId
        );

        HttpHeaders headers = new HttpHeaders();
        addTrustedDeviceCookie(headers, verificationResult.trustedDeviceKeyToSet());
        issueUpdatedSession(headers, verificationResult, currentRefreshToken, httpServletRequest);
        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.of(
                "auth.two_factor_setup_verified",
                messageResolver.get("auth.two_factor_setup_verified")
        ));
    }

    @PostMapping("/2fa/recovery/request")
    public ResponseEntity<ApiResponse<Void>> requestRecovery(Authentication authentication) {
        accountSecurityService.requestTwoFactorRecovery(authentication.getName());
        return ResponseEntity.ok(ApiResponse.of(
                "auth.two_factor_recovery_email_sent",
                messageResolver.get("auth.two_factor_recovery_email_sent")
        ));
    }

    @PostMapping("/2fa/recovery/confirm")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Void>> confirmRecovery(Authentication authentication,
                                                             @Valid @RequestBody ConfirmTwoFactorRecoveryRequest request,
                                                             @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String scope = "account.settings.security.2fa.recovery.confirm";
        String requestHash = idempotencyService.hash(scope, hashPayload(authentication.getName(), request));

        ResponseEntity<ApiResponse> response = idempotencyService.execute(
                scope,
                idempotencyKey,
                requestHash,
                () -> confirmRecoveryInternal(authentication, request)
        );
        return (ResponseEntity<ApiResponse<Void>>) (ResponseEntity<?>) response;
    }

    @PostMapping("/password/change/confirm")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordChange(Authentication authentication,
                                                                   @Valid @RequestBody ChangeAccountPasswordRequest request,
                                                                   @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String scope = "account.settings.security.password.change.confirm";
        String requestHash = idempotencyService.hash(scope, hashPayload(authentication.getName(), request));

        ResponseEntity<ApiResponse> response = idempotencyService.execute(
                scope,
                idempotencyKey,
                requestHash,
                () -> confirmPasswordChangeInternal(authentication, request)
        );
        return (ResponseEntity<ApiResponse<Void>>) (ResponseEntity<?>) response;
    }

    @PostMapping("/password/reset/request")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(Authentication authentication,
                                                                  @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String scope = "account.settings.security.password.reset.request";
        String requestHash = idempotencyService.hash(scope, hashPayload(authentication.getName()));

        ResponseEntity<ApiResponse> response = idempotencyService.execute(
                scope,
                idempotencyKey,
                requestHash,
                () -> requestPasswordResetInternal(authentication)
        );
        return (ResponseEntity<ApiResponse<Void>>) (ResponseEntity<?>) response;
    }

    @DeleteMapping("/trusted-devices/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> revokeTrustedDevice(Authentication authentication,
                                                                 @PathVariable Long sessionId) {
        accountSecurityService.revokeTrustedDevice(authentication.getName(), sessionId);
        return ResponseEntity.ok(ApiResponse.of(
                "auth.trusted_device_revoked",
                messageResolver.get("auth.trusted_device_revoked")
        ));
    }

    @PostMapping("/trusted-devices/revoke-all")
    public ResponseEntity<ApiResponse<Void>> revokeAllTrustedDevices(Authentication authentication) {
        accountSecurityService.revokeAllTrustedDevices(authentication.getName());
        return ResponseEntity.ok(ApiResponse.of(
                "auth.trusted_devices_revoked_all",
                messageResolver.get("auth.trusted_devices_revoked_all")
        ));
    }

    private ResponseEntity<ApiResponse> confirmRecoveryInternal(Authentication authentication,
                                                                ConfirmTwoFactorRecoveryRequest request) {
        accountSecurityService.confirmTwoFactorRecovery(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.of(
                "auth.two_factor_recovery_applied",
                messageResolver.get("auth.two_factor_recovery_applied")
        ));
    }

    private Map<String, Object> hashPayload(String email, ConfirmTwoFactorRecoveryRequest request) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("token", request.getToken());
        return payload;
    }

    private Map<String, Object> hashPayload(String email, ChangeAccountPasswordRequest request) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("newPassword", request.getNewPassword());
        return payload;
    }

    private Map<String, Object> hashPayload(String email) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        return payload;
    }

    private ResponseEntity<ApiResponse> confirmPasswordChangeInternal(Authentication authentication,
                                                                      ChangeAccountPasswordRequest request) {
        accountPasswordSecurityService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.of(
                "auth.password_changed",
                messageResolver.get("auth.password_changed")
        ));
    }

    private ResponseEntity<ApiResponse> requestPasswordResetInternal(Authentication authentication) {
        accountPasswordSecurityService.requestResetEmail(authentication.getName());
        return ResponseEntity.ok(ApiResponse.of(
                "auth.password_reset_email_sent",
                messageResolver.get("auth.password_reset_email_sent")
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

    private void addTrustedDeviceCookie(HttpHeaders headers, String value) {
        headers.add(HttpHeaders.SET_COOKIE, ResponseCookie.from(TRUSTED_DEVICE_COOKIE_NAME, value)
                .httpOnly(jwtProperties.getCookie().isHttpOnly())
                .secure(jwtProperties.getCookie().isSecure())
                .sameSite(jwtProperties.getCookie().getSameSite())
                .path(jwtProperties.getCookie().getPath())
                .maxAge(TRUSTED_DEVICE_COOKIE_MAX_AGE)
                .build().toString());
    }

    private Long resolveSessionId(String currentRefreshToken) {
        if (currentRefreshToken == null || currentRefreshToken.isBlank()) {
            return null;
        }

        try {
            return accountSessionService.getActiveSession(currentRefreshToken).getId();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void issueUpdatedSession(HttpHeaders headers,
                                     TwoFactorSetupVerificationResult verificationResult,
                                     String currentRefreshToken,
                                     HttpServletRequest request) {
        String accessToken = jwtService.generateAccessToken(verificationResult.account());
        String refreshToken = jwtService.generateRefreshToken(verificationResult.account());
        jwtCookieService.addAccessTokenCookie(headers, accessToken);
        jwtCookieService.addRefreshTokenCookie(headers, refreshToken);

        if (currentRefreshToken == null || currentRefreshToken.isBlank()) {
            accountSessionService.createSession(
                    verificationResult.account(),
                    refreshToken,
                    jwtService.extractExpiration(refreshToken, JwtTokenType.REFRESH),
                    request
            );
            return;
        }

        try {
            accountSessionService.rotateSession(
                    verificationResult.account(),
                    currentRefreshToken,
                    refreshToken,
                    jwtService.extractExpiration(refreshToken, JwtTokenType.REFRESH),
                    request
            );
        } catch (RuntimeException exception) {
            accountSessionService.createSession(
                    verificationResult.account(),
                    refreshToken,
                    jwtService.extractExpiration(refreshToken, JwtTokenType.REFRESH),
                    request
            );
        }
    }
}

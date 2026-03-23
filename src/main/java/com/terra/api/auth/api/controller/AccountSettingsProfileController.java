package com.terra.api.auth.api.controller;

import com.terra.api.auth.api.dto.AccountProfileSummaryResponse;
import com.terra.api.auth.api.dto.UpdateProfileRequest;
import com.terra.api.auth.api.dto.UserResponse;
import com.terra.api.auth.application.AccountProfileSummaryService;
import com.terra.api.auth.application.AuthService;
import com.terra.api.common.infrastructure.i18n.MessageResolver;
import com.terra.api.common.infrastructure.web.ApiResponse;
import com.terra.api.idempotency.application.IdempotencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/account/settings/profile")
public class AccountSettingsProfileController {

    private final AuthService authService;
    private final AccountProfileSummaryService accountProfileSummaryService;
    private final MessageResolver messageResolver;
    private final IdempotencyService idempotencyService;

    public AccountSettingsProfileController(AuthService authService,
                                            AccountProfileSummaryService accountProfileSummaryService,
                                            MessageResolver messageResolver,
                                            IdempotencyService idempotencyService) {
        this.authService = authService;
        this.accountProfileSummaryService = accountProfileSummaryService;
        this.messageResolver = messageResolver;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(Authentication authentication) {
        UserResponse user = authService.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.of("auth.profile_loaded", messageResolver.get("auth.profile_loaded"), user));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AccountProfileSummaryResponse>> getProfileSummary(
            Authentication authentication,
            @RequestParam(required = false) java.util.Map<String, String> queryParams) {
        Set<String> supported = Set.of("totalAccounts", "lastLogin", "createdAt");
        List<String> requestedKeys = queryParams == null
                ? List.of()
                : queryParams.keySet().stream().filter(supported::contains).toList();
        boolean requestAll = requestedKeys.isEmpty();

        boolean includeTotalAccounts = requestAll || shouldInclude(queryParams, "totalAccounts");
        boolean includeLastLogin = requestAll || shouldInclude(queryParams, "lastLogin");
        boolean includeCreatedAt = requestAll || shouldInclude(queryParams, "createdAt");

        AccountProfileSummaryResponse summary = accountProfileSummaryService.getSummary(
                authentication.getName(),
                includeTotalAccounts,
                includeLastLogin,
                includeCreatedAt
        );

        return ResponseEntity.ok(ApiResponse.of(
                "auth.profile_summary_loaded",
                messageResolver.get("auth.profile_loaded"),
                summary
        ));
    }

    @SuppressWarnings("unchecked")
    @PatchMapping
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(Authentication authentication,
                                                                   @RequestBody UpdateProfileRequest request,
                                                                   @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String scope = "account.settings.profile.update";
        String requestHash = idempotencyService.hash(scope, hashPayload(authentication.getName(), request));

        ResponseEntity<ApiResponse> response = idempotencyService.execute(
                scope,
                idempotencyKey,
                requestHash,
                () -> updateProfileInternal(authentication, request)
        );
        return (ResponseEntity<ApiResponse<UserResponse>>) (ResponseEntity<?>) response;
    }

    private boolean shouldInclude(java.util.Map<String, String> queryParams, String key) {
        if (queryParams == null || !queryParams.containsKey(key)) {
            return false;
        }

        String rawValue = queryParams.get(key);
        if (rawValue == null || rawValue.isBlank()) {
            return true;
        }

        return Boolean.parseBoolean(rawValue.trim());
    }

    private ResponseEntity<ApiResponse> updateProfileInternal(Authentication authentication, UpdateProfileRequest request) {
        UserResponse user = authService.updateProfile(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.of("auth.profile_updated", messageResolver.get("auth.profile_updated"), user));
    }

    private Map<String, Object> hashPayload(String email, UpdateProfileRequest request) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("username", request.getUsername());
        return payload;
    }
}

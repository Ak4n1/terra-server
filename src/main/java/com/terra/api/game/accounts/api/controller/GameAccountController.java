package com.terra.api.game.accounts.api.controller;

import com.terra.api.common.infrastructure.i18n.MessageResolver;
import com.terra.api.common.infrastructure.web.ApiResponse;
import com.terra.api.game.accounts.api.dto.ChangeGamePasswordRequest;
import com.terra.api.game.accounts.api.dto.CreateGameAccountRequest;
import com.terra.api.game.accounts.api.dto.CreateGameAccountResponse;
import com.terra.api.game.accounts.api.dto.GameAccountSummaryResponse;
import com.terra.api.game.accounts.api.dto.SendChangePasswordCodeRequest;
import com.terra.api.game.accounts.api.dto.VerifyChangePasswordCodeRequest;
import com.terra.api.game.accounts.api.dto.VerifyChangePasswordCodeResponse;
import com.terra.api.game.accounts.api.dto.VerifyCreateCodeRequest;
import com.terra.api.game.accounts.api.dto.VerifyCreateCodeResponse;
import com.terra.api.game.accounts.application.GameAccountChangePasswordService;
import com.terra.api.game.accounts.application.GameAccountCreationService;
import com.terra.api.idempotency.application.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/game-accounts")
public class GameAccountController {

    private final GameAccountCreationService gameAccountCreationService;
    private final GameAccountChangePasswordService gameAccountChangePasswordService;
    private final MessageResolver messageResolver;
    private final IdempotencyService idempotencyService;

    public GameAccountController(GameAccountCreationService gameAccountCreationService,
                                 GameAccountChangePasswordService gameAccountChangePasswordService,
                                 MessageResolver messageResolver,
                                 IdempotencyService idempotencyService) {
        this.gameAccountCreationService = gameAccountCreationService;
        this.gameAccountChangePasswordService = gameAccountChangePasswordService;
        this.messageResolver = messageResolver;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GameAccountSummaryResponse>>> listAccounts(Authentication authentication) {
        List<GameAccountSummaryResponse> response = gameAccountChangePasswordService.listAccounts(authentication.getName());
        return ResponseEntity.ok(ApiResponse.of(
                "game.accounts_listed",
                messageResolver.get("game.change_password.accounts_listed"),
                response
        ));
    }

    @PostMapping("/create-code")
    public ResponseEntity<ApiResponse<Void>> createCode(Authentication authentication) {
        gameAccountCreationService.sendCreateCode(authentication.getName());
        return ResponseEntity.ok(ApiResponse.of(
                "game.create_code_sent",
                messageResolver.get("game.create_code_sent")
        ));
    }

    @PostMapping("/verify-code")
    public ResponseEntity<ApiResponse<VerifyCreateCodeResponse>> verifyCode(Authentication authentication,
                                                                            @Valid @RequestBody VerifyCreateCodeRequest request) {
        VerifyCreateCodeResponse response = gameAccountCreationService.verifyCode(authentication.getName(), request.getCode());
        return ResponseEntity.ok(ApiResponse.of(
                "game.create_code_verified",
                messageResolver.get("game.create_code_verified"),
                response
        ));
    }

    @SuppressWarnings("unchecked")
    @PostMapping
    public ResponseEntity<ApiResponse<CreateGameAccountResponse>> createAccount(Authentication authentication,
                                                                                @Valid @RequestBody CreateGameAccountRequest request,
                                                                                @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String scope = "game.account.create";
        String requestHash = idempotencyService.hash(scope, hashPayload(authentication.getName(), request));

        ResponseEntity<ApiResponse> response = idempotencyService.execute(
                scope,
                idempotencyKey,
                requestHash,
                () -> createAccountInternal(authentication, request)
        );
        return (ResponseEntity<ApiResponse<CreateGameAccountResponse>>) (ResponseEntity<?>) response;
    }

    private ResponseEntity<ApiResponse> createAccountInternal(Authentication authentication, CreateGameAccountRequest request) {
        CreateGameAccountResponse response = gameAccountCreationService.createAccount(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                "game.account_created",
                messageResolver.get("game.account_created"),
                response
        ));
    }

    @PostMapping("/change-password/code")
    public ResponseEntity<ApiResponse<Void>> sendChangePasswordCode(Authentication authentication,
                                                                    @Valid @RequestBody SendChangePasswordCodeRequest request) {
        gameAccountChangePasswordService.sendChangePasswordCode(authentication.getName(), request.getAccountName());
        return ResponseEntity.ok(ApiResponse.of(
                "game.change_password.code_sent",
                messageResolver.get("game.change_password.code_sent")
        ));
    }

    @PostMapping("/change-password/verify")
    public ResponseEntity<ApiResponse<VerifyChangePasswordCodeResponse>> verifyChangePasswordCode(Authentication authentication,
                                                                                                   @Valid @RequestBody VerifyChangePasswordCodeRequest request) {
        VerifyChangePasswordCodeResponse response = gameAccountChangePasswordService.verifyCode(
                authentication.getName(),
                request.getAccountName(),
                request.getCode()
        );
        return ResponseEntity.ok(ApiResponse.of(
                "game.change_password.code_verified",
                messageResolver.get("game.change_password.code_verified"),
                response
        ));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(Authentication authentication,
                                                            @Valid @RequestBody ChangeGamePasswordRequest request,
                                                            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String scope = "game.account.change-password";
        String requestHash = idempotencyService.hash(scope, hashPayload(authentication.getName(), request));

        ResponseEntity<ApiResponse> response = idempotencyService.execute(
                scope,
                idempotencyKey,
                requestHash,
                () -> changePasswordInternal(authentication, request)
        );
        return (ResponseEntity<ApiResponse<Void>>) (ResponseEntity<?>) response;
    }

    private ResponseEntity<ApiResponse> changePasswordInternal(Authentication authentication, ChangeGamePasswordRequest request) {
        gameAccountChangePasswordService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.of(
                "game.change_password.updated",
                messageResolver.get("game.change_password.updated")
        ));
    }

    private Map<String, Object> hashPayload(String email, CreateGameAccountRequest request) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("accountName", request.getAccountName());
        payload.put("verificationToken", request.getVerificationToken());
        return payload;
    }

    private Map<String, Object> hashPayload(String email, ChangeGamePasswordRequest request) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("accountName", request.getAccountName());
        payload.put("newPassword", request.getNewPassword());
        payload.put("verificationToken", request.getVerificationToken());
        return payload;
    }
}


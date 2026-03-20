package com.terra.api.game.accounts.api.controller;

import com.terra.api.common.infrastructure.i18n.MessageResolver;
import com.terra.api.common.infrastructure.web.ApiResponse;
import com.terra.api.game.accounts.api.dto.CreateGameAccountRequest;
import com.terra.api.game.accounts.api.dto.CreateGameAccountResponse;
import com.terra.api.game.accounts.api.dto.VerifyCreateCodeRequest;
import com.terra.api.game.accounts.api.dto.VerifyCreateCodeResponse;
import com.terra.api.game.accounts.application.GameAccountCreationService;
import com.terra.api.idempotency.application.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/game-accounts")
public class GameAccountController {

    private final GameAccountCreationService gameAccountCreationService;
    private final MessageResolver messageResolver;
    private final IdempotencyService idempotencyService;

    public GameAccountController(GameAccountCreationService gameAccountCreationService,
                                 MessageResolver messageResolver,
                                 IdempotencyService idempotencyService) {
        this.gameAccountCreationService = gameAccountCreationService;
        this.messageResolver = messageResolver;
        this.idempotencyService = idempotencyService;
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

    private Map<String, Object> hashPayload(String email, CreateGameAccountRequest request) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("accountName", request.getAccountName());
        payload.put("verificationToken", request.getVerificationToken());
        return payload;
    }
}


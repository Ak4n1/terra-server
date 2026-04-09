package com.terra.api.auth.api.controller;

import com.terra.api.auth.api.dto.AccountActivityListResponse;
import com.terra.api.auth.application.AccountActivityService;
import com.terra.api.common.infrastructure.i18n.MessageResolver;
import com.terra.api.common.infrastructure.web.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/account/settings/activity")
public class AccountSettingsActivityController {

    private final AccountActivityService accountActivityService;
    private final MessageResolver messageResolver;

    public AccountSettingsActivityController(AccountActivityService accountActivityService,
                                             MessageResolver messageResolver) {
        this.accountActivityService = accountActivityService;
        this.messageResolver = messageResolver;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AccountActivityListResponse>> list(Authentication authentication,
                                                                         @RequestParam(name = "page", required = false) Integer page,
                                                                         @RequestParam(name = "size", required = false) Integer size,
                                                                         @RequestParam(name = "sort", required = false) String sort,
                                                                         @RequestParam(name = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                                                         @RequestParam(name = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        AccountActivityListResponse response = accountActivityService.list(authentication.getName(), page, size, sort, dateFrom, dateTo);
        return ResponseEntity.ok(ApiResponse.of(
                "auth.activity_loaded",
                messageResolver.get("auth.activity_loaded"),
                response
        ));
    }
}

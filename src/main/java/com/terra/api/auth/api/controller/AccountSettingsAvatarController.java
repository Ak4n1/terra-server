package com.terra.api.auth.api.controller;

import com.terra.api.auth.api.dto.AvatarSettingsResponse;
import com.terra.api.auth.api.dto.UpdateAvatarPresetRequest;
import com.terra.api.auth.application.AccountAvatarService;
import com.terra.api.common.infrastructure.i18n.MessageResolver;
import com.terra.api.common.infrastructure.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/account/settings/avatar")
public class AccountSettingsAvatarController {

    private final AccountAvatarService accountAvatarService;
    private final MessageResolver messageResolver;

    public AccountSettingsAvatarController(AccountAvatarService accountAvatarService, MessageResolver messageResolver) {
        this.accountAvatarService = accountAvatarService;
        this.messageResolver = messageResolver;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AvatarSettingsResponse>> getSettings(Authentication authentication) {
        AvatarSettingsResponse settings = accountAvatarService.getSettings(authentication.getName());
        return ResponseEntity.ok(ApiResponse.of("auth.avatar_loaded", messageResolver.get("auth.avatar_loaded"), settings));
    }

    @PatchMapping("/preset")
    public ResponseEntity<ApiResponse<AvatarSettingsResponse>> setPreset(Authentication authentication,
                                                                         @Valid @RequestBody UpdateAvatarPresetRequest request) {
        AvatarSettingsResponse settings = accountAvatarService.setPreset(authentication.getName(), request.presetPath());
        return ResponseEntity.ok(ApiResponse.of("auth.avatar_preset_updated", messageResolver.get("auth.avatar_preset_updated"), settings));
    }

    @PatchMapping("/default")
    public ResponseEntity<ApiResponse<AvatarSettingsResponse>> setDefault(Authentication authentication) {
        AvatarSettingsResponse settings = accountAvatarService.setDefault(authentication.getName());
        return ResponseEntity.ok(ApiResponse.of("auth.avatar_default_updated", messageResolver.get("auth.avatar_default_updated"), settings));
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AvatarSettingsResponse>> upload(Authentication authentication,
                                                                      @RequestParam("file") MultipartFile file) {
        AvatarSettingsResponse settings = accountAvatarService.uploadCustom(authentication.getName(), file);
        return ResponseEntity.ok(ApiResponse.of("auth.avatar_custom_uploaded", messageResolver.get("auth.avatar_custom_uploaded"), settings));
    }

    @GetMapping("/custom/current")
    public ResponseEntity<Resource> getCurrentCustomAvatar(Authentication authentication) {
        Resource resource = accountAvatarService.loadCurrentCustomAvatar(authentication.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/webp"))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .body(resource);
    }
}


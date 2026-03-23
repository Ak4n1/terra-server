package com.terra.api.auth.application;

import com.terra.api.auth.api.dto.AvatarSettingsResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AccountAvatarService {
    AvatarSettingsResponse getSettings(String email);

    AvatarSettingsResponse setPreset(String email, String presetPath);

    AvatarSettingsResponse setDefault(String email);

    AvatarSettingsResponse uploadCustom(String email, MultipartFile file);

    Resource loadCurrentCustomAvatar(String email);
}


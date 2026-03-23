package com.terra.api.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAvatarPresetRequest(
        @NotBlank(message = "validation.required")
        @Size(max = 255, message = "validation.max_length")
        String presetPath
) {
}


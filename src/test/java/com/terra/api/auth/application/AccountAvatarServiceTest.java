package com.terra.api.auth.application;

import com.terra.api.auth.api.dto.AvatarSettingsResponse;
import com.terra.api.auth.domain.model.AccountAvatarType;
import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.infrastructure.config.AvatarStorageProperties;
import com.terra.api.auth.infrastructure.persistence.AccountMasterRepository;
import com.terra.api.common.domain.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountAvatarServiceTest {

    @Test
    void shouldAcceptPresetPathWithFormatSubdirectory() {
        AuthService authService = mock(AuthService.class);
        AccountMasterRepository accountMasterRepository = mock(AccountMasterRepository.class);
        AvatarStorageProperties avatarStorageProperties = new AvatarStorageProperties();

        AccountAvatarServiceImpl service = new AccountAvatarServiceImpl(
                authService,
                accountMasterRepository,
                avatarStorageProperties
        );

        AccountMaster account = new AccountMaster();
        ReflectionTestUtils.setField(account, "id", 10L);
        account.setEmail("player@l2terra.online");
        when(authService.getCurrentUserAccount("player@l2terra.online")).thenReturn(account);
        when(accountMasterRepository.save(account)).thenReturn(account);

        AvatarSettingsResponse response = service.setPreset(
                "player@l2terra.online",
                "assets/images/app/avatars/Lineage/webp/FaceIcon_Darkelf_magician_M.webp"
        );

        assertEquals(AccountAvatarType.PRESET.name(), response.avatarType());
        assertEquals(
                "assets/images/app/avatars/Lineage/webp/FaceIcon_Darkelf_magician_M.webp",
                response.presetPath()
        );
        verify(accountMasterRepository).save(account);
    }

    @Test
    void shouldRejectPresetPathTraversalAttempt() {
        AuthService authService = mock(AuthService.class);
        AccountMasterRepository accountMasterRepository = mock(AccountMasterRepository.class);
        AvatarStorageProperties avatarStorageProperties = new AvatarStorageProperties();

        AccountAvatarServiceImpl service = new AccountAvatarServiceImpl(
                authService,
                accountMasterRepository,
                avatarStorageProperties
        );

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.setPreset(
                        "player@l2terra.online",
                        "assets/images/app/avatars/Lineage/webp/../../escape.webp"
                )
        );

        assertEquals("auth.avatar_preset_invalid", exception.getMessage());
        verify(authService, never()).getCurrentUserAccount("player@l2terra.online");
        verify(accountMasterRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}

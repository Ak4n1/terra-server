package com.terra.api.auth.application;

import com.terra.api.auth.api.dto.AvatarSettingsResponse;
import com.terra.api.auth.domain.model.AccountAvatarType;
import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.infrastructure.config.AvatarStorageProperties;
import com.terra.api.auth.infrastructure.persistence.AccountMasterRepository;
import com.terra.api.common.domain.exception.BadRequestException;
import com.terra.api.common.domain.exception.ResourceNotFoundException;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.tika.Tika;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AccountAvatarServiceImpl implements AccountAvatarService {
    private static final Pattern PRESET_PATH_PATTERN = Pattern.compile("^assets/images/app/avatars/(Lineage|Elemental)/[A-Za-z0-9_.-]+\\.(png|jpg|jpeg|webp)$");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final int MIN_IMAGE_SIZE = 64;
    private static final int MAX_IMAGE_SIZE = 4096;

    private final AuthService authService;
    private final AccountMasterRepository accountMasterRepository;
    private final AvatarStorageProperties avatarStorageProperties;
    private final Tika tika = new Tika();

    public AccountAvatarServiceImpl(AuthService authService,
                                    AccountMasterRepository accountMasterRepository,
                                    AvatarStorageProperties avatarStorageProperties) {
        this.authService = authService;
        this.accountMasterRepository = accountMasterRepository;
        this.avatarStorageProperties = avatarStorageProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public AvatarSettingsResponse getSettings(String email) {
        AccountMaster account = authService.getCurrentUserAccount(email);
        return toResponse(account);
    }

    @Override
    @Transactional
    public AvatarSettingsResponse setPreset(String email, String presetPath) {
        String normalizedPresetPath = normalizePresetPath(presetPath);
        if (!PRESET_PATH_PATTERN.matcher(normalizedPresetPath).matches()) {
            throw new BadRequestException("auth.avatar_preset_invalid");
        }

        AccountMaster account = authService.getCurrentUserAccount(email);
        account.setAvatarType(AccountAvatarType.PRESET);
        account.setAvatarPresetPath(normalizedPresetPath);
        account.setAvatarUpdatedAt(Instant.now());
        accountMasterRepository.save(account);
        return toResponse(account);
    }

    @Override
    @Transactional
    public AvatarSettingsResponse setDefault(String email) {
        AccountMaster account = authService.getCurrentUserAccount(email);
        account.setAvatarType(AccountAvatarType.DEFAULT);
        account.setAvatarPresetPath(null);
        account.setAvatarUpdatedAt(Instant.now());
        accountMasterRepository.save(account);
        return toResponse(account);
    }

    @Override
    @Transactional
    public AvatarSettingsResponse uploadCustom(String email, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("auth.avatar_file_required");
        }
        if (file.getSize() > avatarStorageProperties.getMaxBytes()) {
            throw new BadRequestException("auth.avatar_file_too_large");
        }

        byte[] sourceBytes = readFileBytes(file);
        String detectedMimeType = tika.detect(sourceBytes);
        if (!ALLOWED_MIME_TYPES.contains(detectedMimeType)) {
            throw new BadRequestException("auth.avatar_format_invalid");
        }

        BufferedImage sourceImage = readImage(sourceBytes);
        validateDimensions(sourceImage);

        AccountMaster account = authService.getCurrentUserAccount(email);
        Path accountDirectory = resolveAccountDirectory(account.getId());
        createDirectories(accountDirectory);

        String nextFileName = UUID.randomUUID() + ".webp";
        Path targetPath = accountDirectory.resolve(nextFileName).normalize();
        if (!targetPath.startsWith(accountDirectory)) {
            throw new BadRequestException("auth.avatar_format_invalid");
        }

        writeNormalizedWebp(sourceImage, targetPath);
        deletePreviousCustomAvatar(account, accountDirectory);

        account.setAvatarType(AccountAvatarType.CUSTOM);
        account.setAvatarCustomFileName(nextFileName);
        account.setAvatarUpdatedAt(Instant.now());
        accountMasterRepository.save(account);
        return toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadCurrentCustomAvatar(String email) {
        AccountMaster account = authService.getCurrentUserAccount(email);
        if (account.getAvatarType() != AccountAvatarType.CUSTOM || account.getAvatarCustomFileName() == null) {
            throw new ResourceNotFoundException("auth.avatar_custom_not_found");
        }

        Path avatarPath = resolveAccountDirectory(account.getId()).resolve(account.getAvatarCustomFileName()).normalize();
        if (!avatarPath.startsWith(resolveAccountDirectory(account.getId())) || !Files.exists(avatarPath)) {
            throw new ResourceNotFoundException("auth.avatar_custom_not_found");
        }

        return new PathResource(avatarPath);
    }

    private AvatarSettingsResponse toResponse(AccountMaster account) {
        String customUrl = null;
        if (account.getAvatarType() == AccountAvatarType.CUSTOM && account.getAvatarCustomFileName() != null) {
            long version = account.getAvatarUpdatedAt() == null ? 0L : account.getAvatarUpdatedAt().toEpochMilli();
            customUrl = "/api/account/settings/avatar/custom/current?v=" + version;
        }

        return new AvatarSettingsResponse(
                account.getAvatarType() == null ? AccountAvatarType.DEFAULT.name() : account.getAvatarType().name(),
                account.getAvatarPresetPath(),
                customUrl
        );
    }

    private String normalizePresetPath(String presetPath) {
        if (presetPath == null) {
            return "";
        }

        return presetPath.trim()
                .replace('\\', '/')
                .replaceFirst("^/+", "")
                .replace("/avatar_l2/", "/avatars/Lineage/")
                .replace("/avatar/", "/avatars/Lineage/")
                .replace("assets/images/app/avatar_l2/", "assets/images/app/avatars/Lineage/")
                .replace("assets/images/app/avatar/", "assets/images/app/avatars/Lineage/");
    }

    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("auth.avatar_file_read_error");
        }
    }

    private BufferedImage readImage(byte[] sourceBytes) {
        try (InputStream inputStream = new ByteArrayInputStream(sourceBytes)) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new BadRequestException("auth.avatar_format_invalid");
            }
            return image;
        } catch (IOException exception) {
            throw new BadRequestException("auth.avatar_file_read_error");
        }
    }

    private void validateDimensions(BufferedImage image) {
        if (image.getWidth() < MIN_IMAGE_SIZE
                || image.getHeight() < MIN_IMAGE_SIZE
                || image.getWidth() > MAX_IMAGE_SIZE
                || image.getHeight() > MAX_IMAGE_SIZE) {
            throw new BadRequestException("auth.avatar_dimensions_invalid");
        }
    }

    private Path resolveStorageRoot() {
        return Paths.get(avatarStorageProperties.getDir()).toAbsolutePath().normalize();
    }

    private Path resolveAccountDirectory(Long accountId) {
        return resolveStorageRoot().resolve(String.valueOf(accountId)).normalize();
    }

    private void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new BadRequestException("auth.avatar_storage_error");
        }
    }

    private void writeNormalizedWebp(BufferedImage sourceImage, Path targetPath) {
        int outputSize = Math.max(avatarStorageProperties.getOutputSize(), 128);
        try {
            Thumbnails.of(sourceImage)
                    .sourceRegion(Positions.CENTER, Math.min(sourceImage.getWidth(), sourceImage.getHeight()), Math.min(sourceImage.getWidth(), sourceImage.getHeight()))
                    .size(outputSize, outputSize)
                    .outputFormat("webp")
                    .outputQuality(avatarStorageProperties.getWebpQuality())
                    .toFile(targetPath.toFile());
        } catch (IOException exception) {
            throw new BadRequestException("auth.avatar_storage_error");
        }
    }

    private void deletePreviousCustomAvatar(AccountMaster account, Path accountDirectory) {
        String previousFileName = account.getAvatarCustomFileName();
        if (previousFileName == null || previousFileName.isBlank()) {
            return;
        }

        Path previousPath = accountDirectory.resolve(previousFileName).normalize();
        if (!previousPath.startsWith(accountDirectory)) {
            return;
        }

        try {
            Files.deleteIfExists(previousPath);
        } catch (IOException ignored) {
            // Best-effort cleanup, do not block avatar update flow.
        }
    }
}

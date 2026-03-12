package com.terra.api.auth.service;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.AccountVerification;
import com.terra.api.auth.entity.AccountVerificationType;
import com.terra.api.auth.repository.AccountVerificationRepository;
import com.terra.api.common.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class VerificationTokenService {

    private final AccountVerificationRepository accountVerificationRepository;

    public VerificationTokenService(AccountVerificationRepository accountVerificationRepository) {
        this.accountVerificationRepository = accountVerificationRepository;
    }

    @Transactional
    public String createOrRefresh(AccountMaster accountMaster, AccountVerificationType type, long expiresInMinutes) {
        String rawToken = generateToken();
        AccountVerification verification = accountVerificationRepository
                .findByAccount_IdAndType(accountMaster.getId(), type)
                .orElseGet(AccountVerification::new);

        verification.setAccount(accountMaster);
        verification.setType(type);
        verification.setTokenHash(hashToken(rawToken));
        verification.setExpiresAt(Instant.now().plus(expiresInMinutes, ChronoUnit.MINUTES));
        verification.setUsedAt(null);
        accountVerificationRepository.save(verification);
        return rawToken;
    }

    @Transactional(readOnly = true)
    public AccountVerification getActiveVerification(String rawToken, AccountVerificationType type, String errorCode) {
        String tokenHash = hashToken(rawToken);
        AccountVerification verification = accountVerificationRepository
                .findByTokenHashAndType(tokenHash, type)
                .orElseThrow(() -> new BadRequestException(errorCode));

        if (!verification.isActive()) {
            throw new BadRequestException(errorCode);
        }

        return verification;
    }

    public String hashToken(String token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = messageDigest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte hashByte : hashBytes) {
                builder.append(String.format("%02x", hashByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private String generateToken() {
        byte[] randomBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}

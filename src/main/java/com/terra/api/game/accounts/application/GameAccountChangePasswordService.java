package com.terra.api.game.accounts.application;

import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.application.AccountActivityEventKey;
import com.terra.api.auth.application.AccountActivityService;
import com.terra.api.auth.infrastructure.persistence.AccountMasterRepository;
import com.terra.api.common.domain.exception.BadRequestException;
import com.terra.api.common.domain.exception.ForbiddenException;
import com.terra.api.common.domain.exception.TooManyRequestsException;
import com.terra.api.game.accounts.api.dto.ChangeGamePasswordRequest;
import com.terra.api.game.accounts.api.dto.GameAccountSummaryResponse;
import com.terra.api.game.accounts.api.dto.VerifyChangePasswordCodeResponse;
import com.terra.api.game.accounts.domain.port.GameAccountsGateway;
import com.terra.api.game.accounts.infrastructure.mail.GameAccountEmailTemplateService;
import com.terra.api.game.accounts.infrastructure.persistence.jpa.GameAccountPasswordChangeCodeEntity;
import com.terra.api.game.accounts.infrastructure.persistence.jpa.GameAccountPasswordChangeCodeRepository;
import com.terra.api.mail.application.EmailActionCooldownService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class GameAccountChangePasswordService {

    private static final long CODE_EXPIRATION_MINUTES = 5L;
    private static final long CODE_COOLDOWN_SECONDS = 30L;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final String CHANGE_CODE_ACTION = "game-change-password-code";
    private final SecureRandom secureRandom = new SecureRandom();

    private final AccountMasterRepository accountMasterRepository;
    private final GameAccountsGateway gameAccountsGateway;
    private final GameAccountValidationService validationService;
    private final GameAccountPasswordService gameAccountPasswordService;
    private final GameAccountEmailTemplateService gameAccountEmailTemplateService;
    private final EmailActionCooldownService emailActionCooldownService;
    private final GameAccountPasswordChangeCodeRepository passwordChangeCodeRepository;
    private final AccountActivityService accountActivityService;

    public GameAccountChangePasswordService(AccountMasterRepository accountMasterRepository,
                                            GameAccountsGateway gameAccountsGateway,
                                            GameAccountValidationService validationService,
                                            GameAccountPasswordService gameAccountPasswordService,
                                            GameAccountEmailTemplateService gameAccountEmailTemplateService,
                                            EmailActionCooldownService emailActionCooldownService,
                                            GameAccountPasswordChangeCodeRepository passwordChangeCodeRepository,
                                            AccountActivityService accountActivityService) {
        this.accountMasterRepository = accountMasterRepository;
        this.gameAccountsGateway = gameAccountsGateway;
        this.validationService = validationService;
        this.gameAccountPasswordService = gameAccountPasswordService;
        this.gameAccountEmailTemplateService = gameAccountEmailTemplateService;
        this.emailActionCooldownService = emailActionCooldownService;
        this.passwordChangeCodeRepository = passwordChangeCodeRepository;
        this.accountActivityService = accountActivityService;
    }

    @Transactional(readOnly = true)
    public List<GameAccountSummaryResponse> listAccounts(String authenticatedEmail) {
        AccountMaster accountMaster = requireVerifiedAccount(authenticatedEmail);
        String email = normalizeEmail(accountMaster.getEmail());

        return gameAccountsGateway.findByEmailWithCharacters(email).stream()
                .map(summary -> new GameAccountSummaryResponse(
                        summary.login(),
                        summary.email(),
                        summary.createdAt(),
                        summary.lastActiveAt(),
                        summary.charactersCount(),
                        summary.charactersCount() <= 0
                ))
                .toList();
    }

    @Transactional
    public void sendChangePasswordCode(String authenticatedEmail, String rawAccountName) {
        AccountMaster accountMaster = requireVerifiedAccount(authenticatedEmail);
        String email = normalizeEmail(accountMaster.getEmail());
        String accountName = normalizeLogin(rawAccountName);
        validationService.validateAccountName(accountName);
        requireOwnedAccount(accountName, email);

        long retryAfterSeconds = emailActionCooldownService.getRetryAfterSeconds(
                CHANGE_CODE_ACTION,
                email,
                CODE_COOLDOWN_SECONDS
        );
        if (retryAfterSeconds > 0) {
            throw new TooManyRequestsException("game.change_password.code_cooldown_active", retryAfterSeconds);
        }

        emailActionCooldownService.mark(CHANGE_CODE_ACTION, email, CODE_COOLDOWN_SECONDS);

        String rawCode = generateCode();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(CODE_EXPIRATION_MINUTES, ChronoUnit.MINUTES);

        GameAccountPasswordChangeCodeEntity entity = passwordChangeCodeRepository.findByAccountId(accountMaster.getId())
                .orElseGet(GameAccountPasswordChangeCodeEntity::new);
        entity.setAccountId(accountMaster.getId());
        entity.setAccountName(accountName);
        entity.setEmail(email);
        entity.setCodeHash(hash(rawCode));
        entity.setExpiresAt(expiresAt);
        entity.setAttemptCount(0);
        entity.setVerifiedAt(null);
        entity.setConsumedAt(null);
        entity.setVerificationTokenHash(null);
        passwordChangeCodeRepository.save(entity);

        gameAccountEmailTemplateService.sendPasswordChangeCodeEmail(email, accountName, rawCode, CODE_EXPIRATION_MINUTES);
    }

    @Transactional
    public VerifyChangePasswordCodeResponse verifyCode(String authenticatedEmail, String rawAccountName, String rawCode) {
        AccountMaster accountMaster = requireVerifiedAccount(authenticatedEmail);
        String email = normalizeEmail(accountMaster.getEmail());
        String accountName = normalizeLogin(rawAccountName);
        validationService.validateAccountName(accountName);
        requireOwnedAccount(accountName, email);

        GameAccountPasswordChangeCodeEntity entity = passwordChangeCodeRepository.findByAccountId(accountMaster.getId())
                .orElseThrow(() -> new BadRequestException("game.change_password.code_invalid"));

        Instant now = Instant.now();
        if (!accountName.equals(entity.getAccountName())) {
            throw new BadRequestException("game.change_password.verification_required");
        }
        if (entity.getConsumedAt() != null) {
            throw new BadRequestException("game.change_password.verification_required");
        }
        if (entity.getExpiresAt() == null || now.isAfter(entity.getExpiresAt())) {
            throw new BadRequestException("game.change_password.code_expired");
        }
        if (entity.getAttemptCount() >= MAX_VERIFY_ATTEMPTS) {
            long retryAfter = Math.max(1L, now.until(entity.getExpiresAt(), ChronoUnit.SECONDS));
            throw new TooManyRequestsException("game.change_password.code_attempts_exceeded", retryAfter);
        }

        String codeHash = hash(rawCode == null ? "" : rawCode.trim());
        if (!codeHash.equals(entity.getCodeHash())) {
            entity.setAttemptCount(entity.getAttemptCount() + 1);
            passwordChangeCodeRepository.save(entity);
            throw new BadRequestException("game.change_password.code_invalid");
        }

        String verificationToken = generateVerificationToken();
        entity.setVerifiedAt(now);
        entity.setVerificationTokenHash(hash(verificationToken));
        passwordChangeCodeRepository.save(entity);

        long expiresInSeconds = Math.max(1L, now.until(entity.getExpiresAt(), ChronoUnit.SECONDS));
        return new VerifyChangePasswordCodeResponse(verificationToken, expiresInSeconds);
    }

    @Transactional
    public void changePassword(String authenticatedEmail, ChangeGamePasswordRequest request) {
        AccountMaster accountMaster = requireVerifiedAccount(authenticatedEmail);
        String email = normalizeEmail(accountMaster.getEmail());
        String accountName = normalizeLogin(request.getAccountName());
        validationService.validateAccountName(accountName);
        validationService.validatePassword(request.getNewPassword());
        requireOwnedAccount(accountName, email);

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("game.change_password.password_mismatch");
        }

        GameAccountPasswordChangeCodeEntity entity = passwordChangeCodeRepository.findByAccountId(accountMaster.getId())
                .orElseThrow(() -> new BadRequestException("game.change_password.verification_required"));
        Instant now = Instant.now();
        if (!accountName.equals(entity.getAccountName())
                || entity.getConsumedAt() != null
                || entity.getVerifiedAt() == null
                || entity.getVerificationTokenHash() == null
                || entity.getExpiresAt() == null
                || now.isAfter(entity.getExpiresAt())) {
            throw new BadRequestException("game.change_password.verification_required");
        }

        String verificationTokenHash = hash(request.getVerificationToken());
        if (!verificationTokenHash.equals(entity.getVerificationTokenHash())) {
            throw new BadRequestException("game.change_password.verification_required");
        }

        String encodedPassword = gameAccountPasswordService.encodeForGameClient(request.getNewPassword());
        int updatedRows = gameAccountsGateway.updatePassword(accountName, encodedPassword, email);
        if (updatedRows <= 0) {
            throw new BadRequestException("game.change_password.account_not_found");
        }

        entity.setConsumedAt(now);
        passwordChangeCodeRepository.save(entity);
        accountActivityService.log(
                accountMaster,
                AccountActivityEventKey.GAME_ACCOUNT_PASSWORD_CHANGED,
                Map.of("accountName", accountName)
        );
    }

    private void requireOwnedAccount(String accountName, String email) {
        if (!gameAccountsGateway.existsByLoginAndEmail(accountName, email)) {
            throw new BadRequestException("game.change_password.account_not_found");
        }
    }

    private AccountMaster requireVerifiedAccount(String authenticatedEmail) {
        String normalizedEmail = normalizeEmail(authenticatedEmail);
        Optional<AccountMaster> account = accountMasterRepository.findByEmailIgnoreCase(normalizedEmail);
        if (account.isEmpty()) {
            throw new BadRequestException("auth.user_not_found");
        }
        if (!account.get().isEmailVerified()) {
            throw new ForbiddenException("game.master_email_not_verified");
        }
        return account.get();
    }

    private String generateCode() {
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    private String generateVerificationToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            String safeValue = value == null ? "" : value;
            byte[] digest = messageDigest.digest(safeValue.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte hashByte : digest) {
                builder.append(String.format("%02x", hashByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeLogin(String login) {
        return login == null ? "" : login.trim();
    }
}

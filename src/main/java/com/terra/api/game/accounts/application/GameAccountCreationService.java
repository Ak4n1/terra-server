package com.terra.api.game.accounts.application;

import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.infrastructure.persistence.AccountMasterRepository;
import com.terra.api.common.domain.exception.BadRequestException;
import com.terra.api.common.domain.exception.ForbiddenException;
import com.terra.api.common.domain.exception.TooManyRequestsException;
import com.terra.api.game.accounts.api.dto.CreateGameAccountRequest;
import com.terra.api.game.accounts.api.dto.CreateGameAccountResponse;
import com.terra.api.game.accounts.api.dto.VerifyCreateCodeResponse;
import com.terra.api.game.accounts.domain.exception.GameAccountAlreadyExistsException;
import com.terra.api.game.accounts.domain.exception.GameAccountCodeExpiredException;
import com.terra.api.game.accounts.domain.exception.GameAccountCodeInvalidException;
import com.terra.api.game.accounts.domain.exception.GameAccountVerificationRequiredException;
import com.terra.api.game.accounts.domain.port.GameAccountsGateway;
import com.terra.api.game.accounts.infrastructure.mail.GameAccountEmailTemplateService;
import com.terra.api.game.accounts.infrastructure.persistence.jpa.GameAccountCreationCodeEntity;
import com.terra.api.game.accounts.infrastructure.persistence.jpa.GameAccountCreationCodeRepository;
import com.terra.api.mail.application.EmailActionCooldownService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

@Service
public class GameAccountCreationService {

    private static final long CODE_EXPIRATION_MINUTES = 5L;
    private static final long CODE_COOLDOWN_SECONDS = 30L;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final String CREATE_CODE_ACTION = "game-create-account-code";
    private final SecureRandom secureRandom = new SecureRandom();

    private final AccountMasterRepository accountMasterRepository;
    private final GameAccountCreationCodeRepository creationCodeRepository;
    private final GameAccountsGateway gameAccountsGateway;
    private final GameAccountValidationService validationService;
    private final GameAccountPasswordService gameAccountPasswordService;
    private final GameAccountEmailTemplateService gameAccountEmailTemplateService;
    private final EmailActionCooldownService emailActionCooldownService;

    public GameAccountCreationService(AccountMasterRepository accountMasterRepository,
                                      GameAccountCreationCodeRepository creationCodeRepository,
                                      GameAccountsGateway gameAccountsGateway,
                                      GameAccountValidationService validationService,
                                      GameAccountPasswordService gameAccountPasswordService,
                                      GameAccountEmailTemplateService gameAccountEmailTemplateService,
                                      EmailActionCooldownService emailActionCooldownService) {
        this.accountMasterRepository = accountMasterRepository;
        this.creationCodeRepository = creationCodeRepository;
        this.gameAccountsGateway = gameAccountsGateway;
        this.validationService = validationService;
        this.gameAccountPasswordService = gameAccountPasswordService;
        this.gameAccountEmailTemplateService = gameAccountEmailTemplateService;
        this.emailActionCooldownService = emailActionCooldownService;
    }

    @Transactional
    public void sendCreateCode(String authenticatedEmail) {
        AccountMaster accountMaster = requireVerifiedAccount(authenticatedEmail);
        String email = normalizeEmail(accountMaster.getEmail());
        long retryAfterSeconds = emailActionCooldownService.getRetryAfterSeconds(
                CREATE_CODE_ACTION,
                email,
                CODE_COOLDOWN_SECONDS
        );
        if (retryAfterSeconds > 0) {
            throw new TooManyRequestsException("game.create_code_cooldown_active", retryAfterSeconds);
        }

        emailActionCooldownService.mark(CREATE_CODE_ACTION, email, CODE_COOLDOWN_SECONDS);

        String rawCode = generateCode();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(CODE_EXPIRATION_MINUTES, ChronoUnit.MINUTES);

        GameAccountCreationCodeEntity entity = creationCodeRepository.findByAccountId(accountMaster.getId())
                .orElseGet(GameAccountCreationCodeEntity::new);
        entity.setAccountId(accountMaster.getId());
        entity.setEmail(email);
        entity.setCodeHash(hash(rawCode));
        entity.setExpiresAt(expiresAt);
        entity.setAttemptCount(0);
        entity.setVerifiedAt(null);
        entity.setConsumedAt(null);
        entity.setVerificationTokenHash(null);
        creationCodeRepository.save(entity);

        gameAccountEmailTemplateService.sendCreationCodeEmail(email, rawCode, CODE_EXPIRATION_MINUTES);
    }

    @Transactional
    public VerifyCreateCodeResponse verifyCode(String authenticatedEmail, String rawCode) {
        AccountMaster accountMaster = requireVerifiedAccount(authenticatedEmail);
        GameAccountCreationCodeEntity entity = creationCodeRepository.findByAccountId(accountMaster.getId())
                .orElseThrow(GameAccountCodeInvalidException::new);

        Instant now = Instant.now();
        if (entity.getConsumedAt() != null) {
            throw new GameAccountVerificationRequiredException();
        }
        if (entity.getExpiresAt() == null || now.isAfter(entity.getExpiresAt())) {
            throw new GameAccountCodeExpiredException();
        }
        if (entity.getAttemptCount() >= MAX_VERIFY_ATTEMPTS) {
            long retryAfter = Math.max(1L, now.until(entity.getExpiresAt(), ChronoUnit.SECONDS));
            throw new TooManyRequestsException("game.create_code_attempts_exceeded", retryAfter);
        }

        String codeHash = hash(rawCode == null ? "" : rawCode.trim());
        if (!codeHash.equals(entity.getCodeHash())) {
            entity.setAttemptCount(entity.getAttemptCount() + 1);
            creationCodeRepository.save(entity);
            throw new GameAccountCodeInvalidException();
        }

        String verificationToken = generateVerificationToken();
        entity.setVerifiedAt(now);
        entity.setVerificationTokenHash(hash(verificationToken));
        creationCodeRepository.save(entity);

        long expiresInSeconds = Math.max(1L, now.until(entity.getExpiresAt(), ChronoUnit.SECONDS));
        return new VerifyCreateCodeResponse(verificationToken, expiresInSeconds);
    }

    @Transactional
    public CreateGameAccountResponse createAccount(String authenticatedEmail, CreateGameAccountRequest request) {
        AccountMaster accountMaster = requireVerifiedAccount(authenticatedEmail);
        String normalizedLogin = normalizeLogin(request.getAccountName());
        validationService.validateAccountName(normalizedLogin);
        validationService.validatePassword(request.getPassword());

        GameAccountCreationCodeEntity entity = creationCodeRepository.findByAccountId(accountMaster.getId())
                .orElseThrow(GameAccountVerificationRequiredException::new);
        Instant now = Instant.now();
        if (entity.getConsumedAt() != null
                || entity.getVerifiedAt() == null
                || entity.getVerificationTokenHash() == null
                || entity.getExpiresAt() == null
                || now.isAfter(entity.getExpiresAt())) {
            throw new GameAccountVerificationRequiredException();
        }

        String verificationTokenHash = hash(request.getVerificationToken());
        if (!verificationTokenHash.equals(entity.getVerificationTokenHash())) {
            throw new GameAccountVerificationRequiredException();
        }

        if (gameAccountsGateway.existsByLogin(normalizedLogin)) {
            throw new GameAccountAlreadyExistsException();
        }

        String encodedPassword = gameAccountPasswordService.encodeForGameClient(request.getPassword());
        try {
            gameAccountsGateway.createAccount(normalizedLogin, encodedPassword, normalizeEmail(accountMaster.getEmail()));
        } catch (DuplicateKeyException exception) {
            throw new GameAccountAlreadyExistsException();
        }

        entity.setConsumedAt(now);
        creationCodeRepository.save(entity);
        return new CreateGameAccountResponse(normalizedLogin, normalizeEmail(accountMaster.getEmail()), now);
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

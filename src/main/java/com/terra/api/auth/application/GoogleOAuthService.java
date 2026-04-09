package com.terra.api.auth.application;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.domain.model.AuthProvider;
import com.terra.api.auth.domain.model.Role;
import com.terra.api.auth.domain.model.RoleName;
import com.terra.api.auth.infrastructure.config.FirebaseProperties;
import com.terra.api.auth.infrastructure.persistence.AccountMasterRepository;
import com.terra.api.auth.infrastructure.persistence.RoleRepository;
import com.terra.api.common.domain.exception.BadRequestException;
import com.terra.api.common.domain.exception.ForbiddenException;
import com.terra.api.common.domain.exception.ResourceConflictException;
import com.terra.api.common.domain.exception.ResourceNotFoundException;
import com.terra.api.common.domain.i18n.SupportedLanguage;
import com.terra.api.common.infrastructure.i18n.CurrentLanguageResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class GoogleOAuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleOAuthService.class);
    private static final String FIREBASE_APP_NAME = "terra-api-firebase";

    private final AccountMasterRepository accountMasterRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentLanguageResolver currentLanguageResolver;
    private final FirebaseProperties firebaseProperties;
    private final OAuthGoogleEmailCodeService oauthGoogleEmailCodeService;
    private final Object firebaseInitLock = new Object();

    private volatile FirebaseAuth firebaseAuth;

    public GoogleOAuthService(AccountMasterRepository accountMasterRepository,
                              RoleRepository roleRepository,
                              PasswordEncoder passwordEncoder,
                              CurrentLanguageResolver currentLanguageResolver,
                              FirebaseProperties firebaseProperties,
                              OAuthGoogleEmailCodeService oauthGoogleEmailCodeService) {
        this.accountMasterRepository = accountMasterRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentLanguageResolver = currentLanguageResolver;
        this.firebaseProperties = firebaseProperties;
        this.oauthGoogleEmailCodeService = oauthGoogleEmailCodeService;
    }

    @Transactional
    public StartAuthenticationResult startAuthentication(String idToken) {
        GoogleIdentity identity = verifyAndNormalizeIdentity(idToken);
        Instant now = Instant.now();
        LOGGER.info(
                "[OAUTH-GOOGLE] service=GoogleOAuthService action=start-auth identitySubject={} identityEmail={}",
                fingerprint(identity.subject()),
                maskEmail(identity.email())
        );

        Optional<AccountMaster> providerAccount = accountMasterRepository
                .findByAuthProviderAndProviderSubject(AuthProvider.GOOGLE, identity.subject());
        if (providerAccount.isPresent()) {
            LOGGER.info(
                    "[OAUTH-GOOGLE] service=GoogleOAuthService action=start-auth decision=linked-account accountId={} identitySubject={}",
                    providerAccount.get().getId(),
                    fingerprint(identity.subject())
            );
            return StartAuthenticationResult.authenticated(
                    touchLinkedGoogleAccount(providerAccount.get(), identity.email(), now)
            );
        }

        AccountMaster emailAccount = accountMasterRepository.findByEmailIgnoreCase(identity.email()).orElse(null);
        if (emailAccount != null) {
            assertNoProviderConflict(emailAccount, identity.subject());
        }

        SupportedLanguage language = emailAccount != null && emailAccount.getPreferredLanguage() != null
                ? emailAccount.getPreferredLanguage()
                : currentLanguageResolver.resolve();
        OAuthGoogleEmailCodeService.ChallengeDetails challengeDetails = oauthGoogleEmailCodeService.issueChallenge(
                identity.subject(),
                identity.email(),
                language
        );
        LOGGER.info(
                "[OAUTH-GOOGLE] service=GoogleOAuthService action=start-auth decision=email-code-required identitySubject={} challengeId={} maskedEmail={} hasLocalEmailAccount={}",
                fingerprint(identity.subject()),
                challengeDetails.challengeId(),
                challengeDetails.maskedEmail(),
                emailAccount != null
        );

        return StartAuthenticationResult.requiresEmailCode(
                challengeDetails.challengeId(),
                challengeDetails.maskedEmail()
        );
    }

    @Transactional
    public AccountMaster authenticateIfLinked(String idToken) {
        StartAuthenticationResult startResult = startAuthentication(idToken);
        if (startResult.requiresEmailCode()) {
            LOGGER.info(
                    "[OAUTH-GOOGLE] service=GoogleOAuthService action=authenticate-if-linked decision=requires-email-code challengeId={}",
                    startResult.challengeId()
            );
            throw new BadRequestException("auth.oauth_google_email_code_required");
        }
        LOGGER.info(
                "[OAUTH-GOOGLE] service=GoogleOAuthService action=authenticate-if-linked decision=authenticated accountId={} accountEmail={}",
                startResult.account().getId(),
                maskEmail(startResult.account().getEmail())
        );
        return startResult.account();
    }

    @Transactional
    public AccountMaster verifyEmailCodeAndAuthenticate(String challengeId, String code) {
        OAuthGoogleEmailCodeService.VerifiedChallenge verifiedChallenge = oauthGoogleEmailCodeService.verifyCode(challengeId, code);
        LOGGER.info(
                "[OAUTH-GOOGLE] service=GoogleOAuthService action=verify-email-code challengeId={} identitySubject={} identityEmail={}",
                challengeId,
                fingerprint(verifiedChallenge.providerSubject()),
                maskEmail(verifiedChallenge.email())
        );
        GoogleIdentity trustedIdentity = new GoogleIdentity(
                verifiedChallenge.providerSubject(),
                verifiedChallenge.email(),
                true
        );
        AccountMaster accountMaster = resolveOrCreateLinkedAccount(trustedIdentity, Instant.now());
        LOGGER.info(
                "[OAUTH-GOOGLE] service=GoogleOAuthService action=verify-email-code decision=authenticated challengeId={} accountId={} accountEmail={}",
                challengeId,
                accountMaster.getId(),
                maskEmail(accountMaster.getEmail())
        );
        return accountMaster;
    }

    @Transactional
    public ChallengeDetailsResult resendEmailCode(String challengeId) {
        OAuthGoogleEmailCodeService.ChallengeDetails details = oauthGoogleEmailCodeService.resendCode(challengeId);
        LOGGER.info(
                "[OAUTH-GOOGLE] service=GoogleOAuthService action=resend-email-code challengeId={} maskedEmail={}",
                details.challengeId(),
                details.maskedEmail()
        );
        return new ChallengeDetailsResult(details.challengeId(), details.maskedEmail());
    }

    @Transactional
    public AccountMaster authenticate(String idToken) {
        GoogleIdentity identity = verifyAndNormalizeIdentity(idToken);
        AccountMaster accountMaster = resolveOrCreateLinkedAccount(identity, Instant.now());
        LOGGER.info(
                "[OAUTH-GOOGLE] service=GoogleOAuthService action=authenticate accountId={} accountEmail={} identitySubject={}",
                accountMaster.getId(),
                maskEmail(accountMaster.getEmail()),
                fingerprint(identity.subject())
        );
        return accountMaster;
    }

    private GoogleIdentity verifyAndNormalizeIdentity(String idToken) {
        GoogleIdentity identity = verifyGoogleIdentity(idToken);

        if (!identity.emailVerified()) {
            throw new ForbiddenException("auth.oauth_google_email_not_verified");
        }
        if (identity.email() == null || identity.email().isBlank()) {
            throw new BadRequestException("auth.oauth_google_email_missing");
        }

        return new GoogleIdentity(
                identity.subject(),
                identity.email().trim().toLowerCase(Locale.ROOT),
                identity.emailVerified()
        );
    }

    private AccountMaster resolveOrCreateLinkedAccount(GoogleIdentity identity, Instant now) {
        Optional<AccountMaster> providerAccount = accountMasterRepository
                .findByAuthProviderAndProviderSubject(AuthProvider.GOOGLE, identity.subject());
        if (providerAccount.isPresent()) {
            LOGGER.info(
                    "[OAUTH-GOOGLE] service=GoogleOAuthService action=resolve-account decision=provider-subject-match accountId={} identitySubject={}",
                    providerAccount.get().getId(),
                    fingerprint(identity.subject())
            );
            return touchLinkedGoogleAccount(providerAccount.get(), identity.email(), now);
        }

        Optional<AccountMaster> emailAccount = accountMasterRepository.findByEmailIgnoreCase(identity.email());
        boolean willCreate = emailAccount.isEmpty();
        AccountMaster account = emailAccount
                .orElseGet(() -> createGoogleAccount(identity.email(), identity.subject(), now));
        assertNoProviderConflict(account, identity.subject());

        account.setAuthProvider(AuthProvider.GOOGLE);
        account.setProviderSubject(identity.subject());
        account.markEmailVerified(now);
        account.setLastLoginAt(now);
        AccountMaster savedAccount = accountMasterRepository.save(account);
        LOGGER.info(
                "[OAUTH-GOOGLE] service=GoogleOAuthService action=resolve-account decision={} accountId={} accountEmail={} identitySubject={}",
                willCreate ? "created-and-linked" : "linked-existing-email-account",
                savedAccount.getId(),
                maskEmail(savedAccount.getEmail()),
                fingerprint(identity.subject())
        );
        return savedAccount;
    }

    private AccountMaster touchLinkedGoogleAccount(AccountMaster account, String normalizedEmail, Instant now) {
        if (!account.getEmail().equalsIgnoreCase(normalizedEmail)) {
            account.setEmail(normalizedEmail);
        }
        account.markEmailVerified(now);
        account.setLastLoginAt(now);
        return accountMasterRepository.save(account);
    }

    private void assertNoProviderConflict(AccountMaster account, String subject) {
        String existingProviderSubject = account.getProviderSubject();
        if (existingProviderSubject != null
                && !existingProviderSubject.isBlank()
                && !existingProviderSubject.equals(subject)) {
            throw new ResourceConflictException("auth.oauth_google_account_conflict");
        }
    }

    private AccountMaster createGoogleAccount(String normalizedEmail, String subject, Instant now) {
        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new ResourceNotFoundException("auth.default_user_role_not_found"));

        AccountMaster accountMaster = new AccountMaster();
        accountMaster.setEmail(normalizedEmail);
        accountMaster.setPasswordHash(passwordEncoder.encode(UUID.randomUUID() + ":" + subject));
        accountMaster.setAuthProvider(AuthProvider.GOOGLE);
        accountMaster.setProviderSubject(subject);
        accountMaster.markEmailVerified(now);
        accountMaster.setLastLoginAt(now);
        accountMaster.setRoles(Set.of(userRole));
        accountMaster.setPreferredLanguage(currentLanguageResolver.resolve());
        return accountMaster;
    }

    private GoogleIdentity verifyGoogleIdentity(String idToken) {
        try {
            FirebaseToken decodedToken = getFirebaseAuth().verifyIdToken(idToken, true);
            Map<String, Object> claims = decodedToken.getClaims();
            boolean emailVerified = claims.get("email_verified") instanceof Boolean verified && verified;

            validateAudienceIfConfigured(claims);
            GoogleIdentity identity = new GoogleIdentity(decodedToken.getUid(), decodedToken.getEmail(), emailVerified);
            LOGGER.info(
                    "[OAUTH-GOOGLE] service=GoogleOAuthService action=verify-id-token result=valid identitySubject={} identityEmail={} emailVerified={}",
                    fingerprint(identity.subject()),
                    maskEmail(identity.email()),
                    identity.emailVerified()
            );
            return identity;
        } catch (FirebaseAuthException | IllegalArgumentException exception) {
            LOGGER.warn(
                    "[OAUTH-GOOGLE] service=GoogleOAuthService action=verify-id-token result=invalid errorClass={} errorMessage={}",
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            throw new BadRequestException("auth.oauth_google_token_invalid");
        }
    }

    private void validateAudienceIfConfigured(Map<String, Object> claims) {
        String configuredProjectId = firebaseProperties.getProjectId();
        if (configuredProjectId == null || configuredProjectId.isBlank()) {
            return;
        }

        Object audience = claims.get("aud");
        if (audience == null || !configuredProjectId.equals(audience.toString())) {
            throw new BadRequestException("auth.oauth_google_token_invalid");
        }
    }

    private FirebaseAuth getFirebaseAuth() {
        FirebaseAuth current = firebaseAuth;
        if (current != null) {
            return current;
        }

        synchronized (firebaseInitLock) {
            if (firebaseAuth != null) {
                return firebaseAuth;
            }

            if (!firebaseProperties.hasServiceAccountConfigured()) {
                LOGGER.warn("[OAUTH-GOOGLE] service=GoogleOAuthService action=firebase-init result=not-configured");
                throw new BadRequestException("auth.oauth_google_not_configured");
            }

            try {
                GoogleCredentials credentials = loadCredentials();
                FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                        .setCredentials(credentials);
                if (firebaseProperties.getProjectId() != null && !firebaseProperties.getProjectId().isBlank()) {
                    optionsBuilder.setProjectId(firebaseProperties.getProjectId());
                }

                FirebaseApp app = FirebaseApp.getApps().stream()
                        .filter(existing -> FIREBASE_APP_NAME.equals(existing.getName()))
                        .findFirst()
                        .orElseGet(() -> FirebaseApp.initializeApp(optionsBuilder.build(), FIREBASE_APP_NAME));

                firebaseAuth = FirebaseAuth.getInstance(app);
                LOGGER.info(
                        "[OAUTH-GOOGLE] service=GoogleOAuthService action=firebase-init result=success projectId={} appName={}",
                        firebaseProperties.getProjectId(),
                        app.getName()
                );
                return firebaseAuth;
            } catch (IOException exception) {
                LOGGER.warn(
                        "[OAUTH-GOOGLE] service=GoogleOAuthService action=firebase-init result=invalid-config errorMessage={}",
                        exception.getMessage()
                );
                throw new BadRequestException("auth.oauth_google_not_configured");
            }
        }
    }

    private GoogleCredentials loadCredentials() throws IOException {
        String jsonPath = firebaseProperties.getServiceAccountJsonPath();
        if (jsonPath != null && !jsonPath.isBlank()) {
            try (InputStream inputStream = Files.newInputStream(Path.of(jsonPath.trim()))) {
                return GoogleCredentials.fromStream(inputStream);
            }
        }

        String inlineJson = firebaseProperties.getServiceAccountJson();
        if (inlineJson != null && !inlineJson.isBlank()) {
            byte[] rawBytes = resolveInlineJsonBytes(inlineJson.trim());
            try (InputStream inputStream = new ByteArrayInputStream(rawBytes)) {
                return GoogleCredentials.fromStream(inputStream);
            }
        }

        throw new IOException("Firebase service account credentials are missing");
    }

    private byte[] resolveInlineJsonBytes(String value) {
        if (value.startsWith("{")) {
            return value.getBytes(StandardCharsets.UTF_8);
        }

        return Base64.getDecoder().decode(value);
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1 || at >= email.length() - 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String fingerprint(String value) {
        if (value == null || value.isBlank()) {
            return "empty";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 8 && index < hash.length; index++) {
                builder.append(String.format("%02x", hash[index]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            return "sha256-unavailable";
        }
    }

    private record GoogleIdentity(
            String subject,
            String email,
            boolean emailVerified
    ) {
    }

    public record StartAuthenticationResult(
            boolean requiresEmailCode,
            String challengeId,
            String maskedEmail,
            AccountMaster account
    ) {
        public static StartAuthenticationResult authenticated(AccountMaster account) {
            return new StartAuthenticationResult(false, null, null, account);
        }

        public static StartAuthenticationResult requiresEmailCode(String challengeId, String maskedEmail) {
            return new StartAuthenticationResult(true, challengeId, maskedEmail, null);
        }
    }

    public record ChallengeDetailsResult(
            String challengeId,
            String maskedEmail
    ) {
    }
}

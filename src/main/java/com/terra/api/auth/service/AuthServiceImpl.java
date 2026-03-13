package com.terra.api.auth.service;

import com.terra.api.auth.dto.LoginRequest;
import com.terra.api.auth.dto.RegisterRequest;
import com.terra.api.auth.dto.UpdatePreferredLanguageRequest;
import com.terra.api.auth.dto.UserResponse;
import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.Role;
import com.terra.api.auth.entity.RoleName;
import com.terra.api.auth.repository.AccountMasterRepository;
import com.terra.api.common.exception.BadRequestException;
import com.terra.api.common.i18n.model.SupportedLanguage;
import com.terra.api.common.i18n.resolver.CurrentLanguageResolver;
import com.terra.api.notifications.service.NotificationCommandService;
import com.terra.api.auth.repository.RoleRepository;
import com.terra.api.common.exception.ForbiddenException;
import com.terra.api.common.exception.ResourceConflictException;
import com.terra.api.common.exception.ResourceNotFoundException;
import com.terra.api.realtime.service.RealtimeSessionRevocationService;
import com.terra.api.security.service.AccountSessionService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {

    private final AccountMasterRepository accountMasterRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AccountSessionService accountSessionService;
    private final EmailVerificationService emailVerificationService;
    private final RealtimeSessionRevocationService realtimeSessionRevocationService;
    private final NotificationCommandService notificationCommandService;
    private final CurrentLanguageResolver currentLanguageResolver;

    public AuthServiceImpl(
            AccountMasterRepository accountMasterRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            AccountSessionService accountSessionService,
            EmailVerificationService emailVerificationService,
            RealtimeSessionRevocationService realtimeSessionRevocationService,
            NotificationCommandService notificationCommandService,
            CurrentLanguageResolver currentLanguageResolver) {
        this.accountMasterRepository = accountMasterRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.accountSessionService = accountSessionService;
        this.emailVerificationService = emailVerificationService;
        this.realtimeSessionRevocationService = realtimeSessionRevocationService;
        this.notificationCommandService = notificationCommandService;
        this.currentLanguageResolver = currentLanguageResolver;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (accountMasterRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResourceConflictException("auth.email_already_registered");
        }

        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new ResourceNotFoundException("auth.default_user_role_not_found"));

        AccountMaster accountMaster = new AccountMaster();
        accountMaster.setEmail(normalizedEmail);
        accountMaster.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        accountMaster.setEmailVerified(false);
        accountMaster.setRoles(Set.of(userRole));
        accountMaster.setPreferredLanguage(currentLanguageResolver.resolve());

        AccountMaster savedAccountMaster = accountMasterRepository.save(accountMaster);
        emailVerificationService.createOrRefreshEmailVerification(savedAccountMaster);
        notificationCommandService.createWelcomeRegistered(savedAccountMaster);
        return toResponse(savedAccountMaster);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountMaster authenticate(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, request.getPassword()));
        AccountMaster accountMaster = getCurrentUserAccount(normalizedEmail);
        if (!accountMaster.isEmailVerified()) {
            throw new ForbiddenException("auth.email_not_verified");
        }
        return accountMaster;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        return toResponse(getCurrentUserAccount(email));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountMaster getCurrentUserAccount(String email) {
        return accountMasterRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("auth.user_not_found"));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountMaster getCurrentUserAccount(Long accountId) {
        return accountMasterRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("auth.user_not_found"));
    }

    @Override
    @Transactional
    public void revokeAllSessions(String email) {
        AccountMaster accountMaster = getCurrentUserAccount(email);
        accountMaster.setTokenVersion(accountMaster.getTokenVersion() + 1);
        accountSessionService.revokeAllSessions(accountMaster);
        realtimeSessionRevocationService.revokeAccountSessions(accountMaster.getId(), "account_sessions_revoked");
    }

    @Override
    @Transactional
    public UserResponse updatePreferredLanguage(String email, UpdatePreferredLanguageRequest request) {
        SupportedLanguage language = SupportedLanguage.findByCode(request.language())
                .orElseThrow(() -> new BadRequestException("auth.preferred_language_invalid"));
        AccountMaster accountMaster = getCurrentUserAccount(email);
        accountMaster.setPreferredLanguage(language);
        return toResponse(accountMasterRepository.save(accountMaster));
    }

    private UserResponse toResponse(AccountMaster user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.isEnabled(),
                user.isEmailVerified(),
                user.getPreferredLanguage().getCode(),
                user.getRoles().stream().map(role -> role.getName().name()).collect(Collectors.toSet()),
                user.getCreatedAt());
    }
}

package com.terra.api.security.infrastructure.jwt;

import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.infrastructure.persistence.AccountMasterRepository;
import com.terra.api.security.domain.JwtAuthenticationException;
import com.terra.api.security.domain.JwtTokenType;
import com.terra.api.security.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;
    private final AccountMasterRepository accountMasterRepository;

    public JwtService(JwtProperties jwtProperties, AccountMasterRepository accountMasterRepository) {
        this.jwtProperties = jwtProperties;
        this.accountMasterRepository = accountMasterRepository;
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(AccountMaster accountMaster) {
        return generateToken(
                accountMaster,
                JwtTokenType.ACCESS,
                Instant.now().plus(jwtProperties.getAccessTokenExpirationMinutes(), ChronoUnit.MINUTES)
        );
    }

    public String generateRefreshToken(AccountMaster accountMaster) {
        return generateToken(
                accountMaster,
                JwtTokenType.REFRESH,
                Instant.now().plus(jwtProperties.getRefreshTokenExpirationDays(), ChronoUnit.DAYS)
        );
    }

    public String extractAccountPublicId(String token, JwtTokenType expectedTokenType) {
        return extractAccountPublicId(token, expectedTokenType, jwtProperties.getAudienceApi());
    }

    public String extractAccountPublicId(String token, JwtTokenType expectedTokenType, String expectedAudience) {
        Claims claims = parseClaims(token, expectedTokenType, expectedAudience);
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }
        return subject;
    }

    public Instant extractExpiration(String token, JwtTokenType expectedTokenType) {
        Claims claims = parseClaims(token, expectedTokenType, jwtProperties.getAudienceApi());

        Date expiration = claims.getExpiration();
        if (expiration == null) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }
        return expiration.toInstant();
    }

    public long extractTokenVersion(String token, JwtTokenType expectedTokenType) {
        return extractTokenVersion(token, expectedTokenType, jwtProperties.getAudienceApi());
    }

    public long extractTokenVersion(String token, JwtTokenType expectedTokenType, String expectedAudience) {
        Claims claims = parseClaims(token, expectedTokenType, expectedAudience);
        Number tokenVersion = claims.get("ver", Number.class);
        if (tokenVersion == null) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }
        return tokenVersion.longValue();
    }

    private String generateToken(AccountMaster accountMaster, JwtTokenType tokenType, Instant expiresAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(resolveSubject(accountMaster))
                .id(UUID.randomUUID().toString())
                .claim("type", tokenType.name())
                .claim("ver", accountMaster.getTokenVersion())
                .issuer(jwtProperties.getIssuer())
                .claim("aud", resolveAudiences(tokenType))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    private String resolveSubject(AccountMaster accountMaster) {
        String publicId = accountMaster.getPublicId();
        if (publicId != null && !publicId.isBlank()) {
            return publicId;
        }

        accountMaster.setPublicId(UUID.randomUUID().toString());
        return accountMasterRepository.save(accountMaster).getPublicId();
    }

    private Claims parseClaims(String token, JwtTokenType expectedTokenType, String expectedAudience) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            Claims claims = jws.getPayload();
            validateAlgorithm(jws);
            validateIssuer(claims);
            validateAudience(claims, expectedAudience);
            String tokenType = claims.get("type", String.class);
            if (tokenType == null || !expectedTokenType.name().equals(tokenType)) {
                throw new JwtAuthenticationException("auth.invalid_token");
            }
            return claims;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }
    }

    private void validateAlgorithm(Jws<Claims> jws) {
        String algorithm = jws.getHeader().getAlgorithm();
        if (algorithm == null) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }

        Set<String> allowed = resolveAllowedAlgorithms();
        if (!allowed.contains(algorithm.toUpperCase(Locale.ROOT))) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }
    }

    private void validateIssuer(Claims claims) {
        String expectedIssuer = jwtProperties.getIssuer();
        if (expectedIssuer == null || expectedIssuer.isBlank()) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }

        String issuer = claims.getIssuer();
        if (!expectedIssuer.equals(issuer)) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }
    }

    private void validateAudience(Claims claims, String expectedAudience) {
        if (expectedAudience == null || expectedAudience.isBlank()) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }

        Object audienceClaim = claims.get("aud");
        if (audienceClaim == null) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }

        boolean matches = false;
        if (audienceClaim instanceof String audience) {
            matches = expectedAudience.equals(audience);
        } else if (audienceClaim instanceof Collection<?> audiences) {
            matches = audiences.stream().filter(String.class::isInstance)
                    .map(String.class::cast)
                    .anyMatch(expectedAudience::equals);
        }

        if (!matches) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }
    }

    private Set<String> resolveAllowedAlgorithms() {
        List<String> configured = jwtProperties.getAllowedAlgorithms();
        if (configured == null || configured.isEmpty()) {
            return Set.of("HS256");
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String configuredAlgorithm : configured) {
            if (configuredAlgorithm == null || configuredAlgorithm.isBlank()) {
                continue;
            }
            normalized.add(configuredAlgorithm.trim().toUpperCase(Locale.ROOT));
        }
        return normalized.isEmpty() ? Set.of("HS256") : normalized;
    }

    private Object resolveAudiences(JwtTokenType tokenType) {
        if (tokenType == JwtTokenType.ACCESS) {
            Set<String> audiences = new LinkedHashSet<>();
            if (jwtProperties.getAudienceApi() != null && !jwtProperties.getAudienceApi().isBlank()) {
                audiences.add(jwtProperties.getAudienceApi());
            }
            if (jwtProperties.getAudienceRealtime() != null && !jwtProperties.getAudienceRealtime().isBlank()) {
                audiences.add(jwtProperties.getAudienceRealtime());
            }
            return audiences;
        }

        if (jwtProperties.getAudienceApi() == null || jwtProperties.getAudienceApi().isBlank()) {
            return Set.of();
        }
        return Set.of(jwtProperties.getAudienceApi());
    }
}

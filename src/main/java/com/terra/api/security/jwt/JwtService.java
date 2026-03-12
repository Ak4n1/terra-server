package com.terra.api.security.jwt;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.security.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
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

    public Long extractAccountId(String token, JwtTokenType expectedTokenType) {
        Claims claims = parseClaims(token, expectedTokenType);

        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException exception) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }
    }

    public Instant extractExpiration(String token, JwtTokenType expectedTokenType) {
        Claims claims = parseClaims(token, expectedTokenType);

        Date expiration = claims.getExpiration();
        if (expiration == null) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }
        return expiration.toInstant();
    }

    public long extractTokenVersion(String token, JwtTokenType expectedTokenType) {
        Claims claims = parseClaims(token, expectedTokenType);
        Number tokenVersion = claims.get("ver", Number.class);
        if (tokenVersion == null) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }
        return tokenVersion.longValue();
    }

    private String generateToken(AccountMaster accountMaster, JwtTokenType tokenType, Instant expiresAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(accountMaster.getId()))
                .id(UUID.randomUUID().toString())
                .claim("type", tokenType.name())
                .claim("ver", accountMaster.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    private Claims parseClaims(String token, JwtTokenType expectedTokenType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String tokenType = claims.get("type", String.class);
            if (tokenType == null || !expectedTokenType.name().equals(tokenType)) {
                throw new JwtAuthenticationException("auth.invalid_token");
            }
            return claims;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }
    }
}

package com.terra.api.security.infrastructure.filter;

import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.infrastructure.persistence.AccountMasterRepository;
import com.terra.api.security.infrastructure.config.JwtProperties;
import com.terra.api.security.infrastructure.jwt.JwtService;
import com.terra.api.security.domain.JwtTokenType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AccountMasterRepository accountMasterRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   JwtProperties jwtProperties,
                                   AccountMasterRepository accountMasterRepository) {
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.accountMasterRepository = accountMasterRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String accessToken = extractCookieValue(request, jwtProperties.getAccessCookieName());
            if (accessToken != null && !accessToken.isBlank()) {
                try {
                    String accountPublicId = jwtService.extractAccountPublicId(
                            accessToken,
                            JwtTokenType.ACCESS,
                            jwtProperties.getAudienceApi()
                    );
                    AccountMaster accountMaster = accountMasterRepository.findByPublicId(accountPublicId).orElse(null);
                    long tokenVersion = jwtService.extractTokenVersion(
                            accessToken,
                            JwtTokenType.ACCESS,
                            jwtProperties.getAudienceApi()
                    );
                    if (accountMaster != null
                            && accountMaster.isEnabled()
                            && tokenVersion == accountMaster.getTokenVersion()) {
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                accountMaster.getEmail(),
                                null,
                                accountMaster.getRoles().stream()
                                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                                .collect(Collectors.toSet())
                        );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (RuntimeException ignored) {
                    SecurityContextHolder.clearContext();
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}

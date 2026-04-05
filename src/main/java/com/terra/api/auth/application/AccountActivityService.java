package com.terra.api.auth.application;

import com.terra.api.auth.api.dto.AccountActivityEntryResponse;
import com.terra.api.auth.api.dto.AccountActivityListResponse;
import com.terra.api.auth.domain.model.AccountActivityEvent;
import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.infrastructure.persistence.AccountActivityEventRepository;
import com.terra.api.auth.infrastructure.persistence.AccountMasterRepository;
import com.terra.api.common.domain.exception.ResourceNotFoundException;
import com.terra.api.security.application.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class AccountActivityService {
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    private final AccountActivityEventRepository accountActivityEventRepository;
    private final AccountMasterRepository accountMasterRepository;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public AccountActivityService(AccountActivityEventRepository accountActivityEventRepository,
                                  AccountMasterRepository accountMasterRepository,
                                  ObjectMapper objectMapper,
                                  ClientIpResolver clientIpResolver,
                                  ObjectProvider<HttpServletRequest> requestProvider) {
        this.accountActivityEventRepository = accountActivityEventRepository;
        this.accountMasterRepository = accountMasterRepository;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
        this.requestProvider = requestProvider;
    }

    @Transactional
    public void log(AccountMaster account, String eventKey, Map<String, Object> metadata) {
        if (account == null || account.getId() == null || eventKey == null || eventKey.isBlank()) {
            return;
        }

        AccountActivityEvent event = new AccountActivityEvent();
        event.setAccount(account);
        event.setEventKey(eventKey.trim());
        event.setMetadataJson(writeMetadata(sanitizeMetadata(metadata)));

        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request != null) {
            event.setIpAddress(truncate(clientIpResolver.resolve(request), 64));
            event.setUserAgent(resolveUserAgent(request));
        }

        accountActivityEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public AccountActivityListResponse list(String email, Integer requestedPage, Integer requestedSize, String requestedSort) {
        AccountMaster accountMaster = accountMasterRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("auth.user_not_found"));

        int page = normalizePage(requestedPage);
        int size = normalizeSize(requestedSize);
        Sort.Direction sortDirection = normalizeSortDirection(requestedSort);
        Page<AccountActivityEvent> result = accountActivityEventRepository.findByAccount_Id(
                accountMaster.getId(),
                PageRequest.of(page, size, Sort.by(sortDirection, "occurredAt", "id"))
        );

        return new AccountActivityListResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.hasNext(),
                result.getNumber(),
                result.getSize()
        );
    }

    private AccountActivityEntryResponse toResponse(AccountActivityEvent event) {
        return new AccountActivityEntryResponse(
                event.getEventKey(),
                parseMetadata(event.getMetadataJson()),
                event.getIpAddress(),
                event.getUserAgent(),
                event.getOccurredAt()
        );
    }

    private int normalizePage(Integer requestedPage) {
        if (requestedPage == null) {
            return 0;
        }
        return Math.max(0, requestedPage);
    }

    private int normalizeSize(Integer requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(requestedSize, MAX_SIZE));
    }

    private Sort.Direction normalizeSortDirection(String requestedSort) {
        if (requestedSort == null || requestedSort.isBlank()) {
            return DEFAULT_SORT_DIRECTION;
        }

        String normalized = requestedSort.trim().toLowerCase(Locale.ROOT);
        if ("asc".equals(normalized)) {
            return Sort.Direction.ASC;
        }
        if ("desc".equals(normalized)) {
            return Sort.Direction.DESC;
        }

        return DEFAULT_SORT_DIRECTION;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveUserAgent(HttpServletRequest request) {
        return truncate(request.getHeader("User-Agent"), 512);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) {
                return;
            }

            if (value instanceof String stringValue) {
                String normalized = truncate(stringValue, 200);
                if (normalized != null) {
                    sanitized.put(key, normalized);
                }
                return;
            }

            if (value instanceof Number || value instanceof Boolean) {
                sanitized.put(key, value);
            }
        });

        return sanitized;
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception exception) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, Object> parsed = objectMapper.readValue(value, LinkedHashMap.class);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception exception) {
            return Map.of();
        }
    }
}

package com.terra.api.idempotency.application;

import com.terra.api.common.domain.exception.BadRequestException;
import com.terra.api.common.domain.exception.ResourceConflictException;
import com.terra.api.idempotency.domain.model.IdempotencyRequest;
import com.terra.api.idempotency.domain.model.IdempotencyStatus;
import com.terra.api.idempotency.infrastructure.persistence.IdempotencyRequestRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class IdempotencyService {

    private static final int MAX_SCOPE_LENGTH = 120;
    private static final int MAX_KEY_LENGTH = 128;
    private static final int REQUEST_HASH_LENGTH = 64;
    private static final long DEFAULT_TTL_SECONDS = 24 * 60 * 60;

    private final IdempotencyRequestRepository idempotencyRequestRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public IdempotencyService(IdempotencyRequestRepository idempotencyRequestRepository,
                              ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager) {
        this.idempotencyRequestRepository = idempotencyRequestRepository;
        this.objectMapper = objectMapper;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String hash(String scope, Object payload) {
        validateScope(scope);
        String canonicalPayload = toCanonicalJson(payload == null ? Map.of() : payload);
        return sha256Hex(scope + ":" + canonicalPayload);
    }

    public <T> ResponseEntity<T> execute(String scope,
                                         String idempotencyKey,
                                         String requestHash,
                                         Supplier<ResponseEntity<T>> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        String normalizedScope = normalizeScope(scope);
        String normalizedKey = normalizeKey(idempotencyKey);
        validateRequestHash(requestHash);

        Reservation reservation = reserve(normalizedScope, normalizedKey, requestHash);
        if (reservation.replayResponse() != null) {
            return reservation.replayResponse();
        }

        try {
            ResponseEntity<T> response = action.get();
            complete(reservation.recordId(), response);
            return response;
        } catch (RuntimeException exception) {
            release(reservation.recordId());
            throw exception;
        }
    }

    protected Reservation reserve(String scope, String idempotencyKey, String requestHash) {
        Reservation reservation = requiresNewTransactionTemplate.execute(status -> {
            Instant now = Instant.now();
            IdempotencyRequest existing = idempotencyRequestRepository
                    .findWithLockByScopeAndIdempotencyKey(scope, idempotencyKey)
                    .orElse(null);

            if (existing != null) {
                if (existing.getExpiresAt().isBefore(now)) {
                    idempotencyRequestRepository.delete(existing);
                } else {
                    if (!existing.getRequestHash().equals(requestHash)) {
                        throw new ResourceConflictException("common.idempotency_key_payload_mismatch");
                    }

                    if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                        return new Reservation(existing.getId(), deserializeStoredResponse(existing));
                    }

                    throw new ResourceConflictException("common.idempotency_key_in_progress");
                }
            }

            IdempotencyRequest request = new IdempotencyRequest();
            request.setScope(scope);
            request.setIdempotencyKey(idempotencyKey);
            request.setRequestHash(requestHash);
            request.setStatus(IdempotencyStatus.IN_PROGRESS);
            request.setExpiresAt(now.plusSeconds(DEFAULT_TTL_SECONDS));

            IdempotencyRequest saved = idempotencyRequestRepository.save(request);
            return new Reservation(saved.getId(), null);
        });

        if (reservation == null) {
            throw new IllegalStateException("idempotency reservation transaction returned null");
        }

        return reservation;
    }

    protected <T> void complete(Long id, ResponseEntity<T> responseEntity) {
        requiresNewTransactionTemplate.executeWithoutResult(status -> {
            IdempotencyRequest request = idempotencyRequestRepository.findById(id)
                    .orElseThrow(() -> new IllegalStateException("idempotency request not found"));

            request.setStatus(IdempotencyStatus.COMPLETED);
            request.setResponseStatus(responseEntity.getStatusCode().value());
            request.setResponseHeadersJson(writeHeaders(responseEntity.getHeaders()));
            request.setResponseBodyJson(writeBody(responseEntity.getBody()));
            request.setExpiresAt(Instant.now().plusSeconds(DEFAULT_TTL_SECONDS));
        });
    }

    protected void release(Long id) {
        requiresNewTransactionTemplate.executeWithoutResult(status ->
                idempotencyRequestRepository.findById(id).ifPresent(idempotencyRequestRepository::delete)
        );
    }

    private void validateScope(String scope) {
        if (scope == null || scope.isBlank() || scope.length() > MAX_SCOPE_LENGTH) {
            throw new BadRequestException("common.idempotency_scope_invalid");
        }
    }

    private String normalizeScope(String scope) {
        String normalizedScope = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
        validateScope(normalizedScope);
        return normalizedScope;
    }

    private String normalizeKey(String idempotencyKey) {
        String normalizedKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (normalizedKey.isBlank() || normalizedKey.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException("common.idempotency_key_invalid");
        }
        return normalizedKey;
    }

    private void validateRequestHash(String requestHash) {
        if (requestHash == null || requestHash.length() != REQUEST_HASH_LENGTH) {
            throw new BadRequestException("common.idempotency_hash_invalid");
        }
    }

    private String toCanonicalJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(canonicalize(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to canonicalize idempotency payload", exception);
        }
    }

    private String writeBody(Object body) {
        if (body == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize idempotent response body", exception);
        }
    }

    private String writeHeaders(HttpHeaders headers) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        headers.forEach((name, values) -> map.put(name, List.copyOf(values)));
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize idempotent response headers", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> deserializeStoredResponse(IdempotencyRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (request.getResponseHeadersJson() != null && !request.getResponseHeadersJson().isBlank()) {
            Map<String, List<String>> map = readHeaders(request.getResponseHeadersJson());
            map.forEach(headers::put);
        }

        T body = null;
        if (request.getResponseBodyJson() != null && !request.getResponseBodyJson().isBlank()) {
            body = (T) readBody(request.getResponseBodyJson());
        }

        HttpStatusCode statusCode = HttpStatusCode.valueOf(request.getResponseStatus());
        return new ResponseEntity<>(body, headers, statusCode);
    }

    private Map<String, List<String>> readHeaders(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize idempotent response headers", exception);
        }
    }

    private Object readBody(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize idempotent response body", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), canonicalize(entry.getValue()));
            }
            return sorted;
        }

        if (value instanceof List<?> listValue) {
            List<Object> normalized = new ArrayList<>(listValue.size());
            for (Object element : listValue) {
                normalized.add(canonicalize(element));
            }
            return normalized;
        }

        return value;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte hashByte : digest) {
                builder.append(String.format("%02x", hashByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    protected static final class Reservation {
        private final Long recordId;
        private final ResponseEntity<?> replayResponse;

        protected Reservation(Long recordId, ResponseEntity<?> replayResponse) {
            this.recordId = recordId;
            this.replayResponse = replayResponse;
        }

        protected Long recordId() {
            return recordId;
        }

        @SuppressWarnings("unchecked")
        protected <T> ResponseEntity<T> replayResponse() {
            return (ResponseEntity<T>) replayResponse;
        }
    }
}

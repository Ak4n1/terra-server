package com.terra.api.notifications.api.controller;

import com.terra.api.common.infrastructure.i18n.MessageResolver;
import com.terra.api.common.infrastructure.web.ApiResponse;
import com.terra.api.idempotency.application.IdempotencyService;
import com.terra.api.notifications.application.NotificationAdminService;
import com.terra.api.notifications.api.dto.NotificationBroadcastRequest;
import com.terra.api.notifications.api.dto.NotificationBroadcastResponse;
import com.terra.api.notifications.api.dto.NotificationAdminAuditListResponse;
import com.terra.api.notifications.api.dto.NotificationDispatchRequest;
import com.terra.api.notifications.api.dto.NotificationMutationResponse;
import com.terra.api.notifications.api.dto.NotificationTemplateResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/notifications")
public class NotificationAdminController {

    private final NotificationAdminService notificationAdminService;
    private final MessageResolver messageResolver;
    private final IdempotencyService idempotencyService;

    public NotificationAdminController(NotificationAdminService notificationAdminService,
                                       MessageResolver messageResolver,
                                       IdempotencyService idempotencyService) {
        this.notificationAdminService = notificationAdminService;
        this.messageResolver = messageResolver;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<NotificationTemplateResponse>>> listTemplates() {
        List<NotificationTemplateResponse> response = notificationAdminService.listAvailableTemplates();
        return ResponseEntity.ok(ApiResponse.of(
                "notifications.admin_templates_loaded",
                messageResolver.get("notifications.admin_templates_loaded"),
                response
        ));
    }

    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<NotificationAdminAuditListResponse>> listAudit(@RequestParam(defaultValue = "0") int page,
                                                                                     @RequestParam(defaultValue = "4") int size,
                                                                                     @RequestParam(required = false) String template,
                                                                                     @RequestParam(required = false) String status,
                                                                                     @RequestParam(required = false) String dateFrom,
                                                                                     @RequestParam(required = false) String dateTo) {
        NotificationAdminAuditListResponse response = notificationAdminService.listAudit(page, size, template, status, dateFrom, dateTo);
        return ResponseEntity.ok(ApiResponse.of(
                "notifications.admin_audit_loaded",
                messageResolver.get("notifications.admin_audit_loaded"),
                response
        ));
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<NotificationMutationResponse>> dispatch(@RequestBody(required = false) NotificationDispatchRequest request,
                                                                              @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        NotificationDispatchRequest resolvedRequest = request == null
                ? new NotificationDispatchRequest(null, null, null)
                : request;

        String requestHash = idempotencyService.hash(
                "notifications.admin.dispatch",
                hashPayloadForDispatch(resolvedRequest)
        );

        ResponseEntity<ApiResponse> response = idempotencyService.execute(
                "notifications.admin.dispatch",
                idempotencyKey,
                requestHash,
                () -> dispatchInternal(resolvedRequest)
        );
        return (ResponseEntity<ApiResponse<NotificationMutationResponse>>) (ResponseEntity<?>) response;
    }

    @PostMapping("/broadcast")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<NotificationBroadcastResponse>> broadcast(@RequestBody(required = false) NotificationBroadcastRequest request,
                                                                                @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        NotificationBroadcastRequest resolvedRequest = request == null
                ? new NotificationBroadcastRequest(null, null, null, null)
                : request;

        String requestHash = idempotencyService.hash(
                "notifications.admin.broadcast",
                hashPayloadForBroadcast(resolvedRequest)
        );

        ResponseEntity<ApiResponse> response = idempotencyService.execute(
                "notifications.admin.broadcast",
                idempotencyKey,
                requestHash,
                () -> broadcastInternal(resolvedRequest)
        );
        return (ResponseEntity<ApiResponse<NotificationBroadcastResponse>>) (ResponseEntity<?>) response;
    }

    private ResponseEntity<ApiResponse> dispatchInternal(NotificationDispatchRequest request) {
        NotificationMutationResponse response = notificationAdminService.dispatch(request);
        return ResponseEntity.ok(ApiResponse.of(
                "notifications.admin_dispatch_success",
                messageResolver.get("notifications.admin_dispatch_success"),
                response
        ));
    }

    private ResponseEntity<ApiResponse> broadcastInternal(NotificationBroadcastRequest request) {
        NotificationBroadcastResponse response = notificationAdminService.broadcast(request);
        return ResponseEntity.ok(ApiResponse.of(
                "notifications.admin_broadcast_success",
                messageResolver.get("notifications.admin_broadcast_success"),
                response
        ));
    }

    private Map<String, Object> hashPayloadForDispatch(NotificationDispatchRequest request) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", request.email());
        payload.put("template", request.template());
        payload.put("params", request.params() == null ? Map.of() : request.params());
        return payload;
    }

    private Map<String, Object> hashPayloadForBroadcast(NotificationBroadcastRequest request) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("template", request.template());
        payload.put("params", request.params() == null ? Map.of() : request.params());
        payload.put("targetType", request.targetType());
        payload.put("targetValue", request.targetValue());
        return payload;
    }
}

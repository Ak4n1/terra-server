package com.terra.api.notifications.admin;

import com.terra.api.common.i18n.message.MessageResolver;
import com.terra.api.common.response.ApiResponse;
import com.terra.api.notifications.dto.NotificationBroadcastRequest;
import com.terra.api.notifications.dto.NotificationBroadcastResponse;
import com.terra.api.notifications.dto.NotificationDispatchRequest;
import com.terra.api.notifications.dto.NotificationMutationResponse;
import com.terra.api.notifications.dto.NotificationTemplateResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/notifications")
public class NotificationAdminController {

    private final NotificationAdminService notificationAdminService;
    private final MessageResolver messageResolver;

    public NotificationAdminController(NotificationAdminService notificationAdminService,
                                       MessageResolver messageResolver) {
        this.notificationAdminService = notificationAdminService;
        this.messageResolver = messageResolver;
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

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationMutationResponse>> dispatch(@RequestBody(required = false) NotificationDispatchRequest request) {
        NotificationMutationResponse response = notificationAdminService.dispatch(
                request == null ? new NotificationDispatchRequest(null, null, null) : request
        );
        return ResponseEntity.ok(ApiResponse.of(
                "notifications.admin_dispatch_success",
                messageResolver.get("notifications.admin_dispatch_success"),
                response
        ));
    }

    @PostMapping("/broadcast")
    public ResponseEntity<ApiResponse<NotificationBroadcastResponse>> broadcast(@RequestBody(required = false) NotificationBroadcastRequest request) {
        NotificationBroadcastResponse response = notificationAdminService.broadcast(
                request == null ? new NotificationBroadcastRequest(null, null, null, null) : request
        );
        return ResponseEntity.ok(ApiResponse.of(
                "notifications.admin_broadcast_success",
                messageResolver.get("notifications.admin_broadcast_success"),
                response
        ));
    }
}

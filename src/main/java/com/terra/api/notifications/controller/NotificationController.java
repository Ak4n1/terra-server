package com.terra.api.notifications.controller;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.service.AuthService;
import com.terra.api.common.i18n.message.MessageResolver;
import com.terra.api.common.response.ApiResponse;
import com.terra.api.notifications.dto.NotificationBulkMutationResponse;
import com.terra.api.notifications.dto.NotificationListResponse;
import com.terra.api.notifications.dto.NotificationMutationResponse;
import com.terra.api.notifications.service.NotificationCommandService;
import com.terra.api.notifications.service.NotificationQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final AuthService authService;
    private final NotificationQueryService notificationQueryService;
    private final NotificationCommandService notificationCommandService;
    private final MessageResolver messageResolver;

    public NotificationController(AuthService authService,
                                  NotificationQueryService notificationQueryService,
                                  NotificationCommandService notificationCommandService,
                                  MessageResolver messageResolver) {
        this.authService = authService;
        this.notificationQueryService = notificationQueryService;
        this.notificationCommandService = notificationCommandService;
        this.messageResolver = messageResolver;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> list(Authentication authentication,
                                                                      @RequestParam(name = "limit", required = false) Integer limit,
                                                                      @RequestParam(name = "page", required = false) Integer page,
                                                                      @RequestParam(name = "unreadOnly", required = false) Boolean unreadOnly) {
        AccountMaster account = authService.getCurrentUserAccount(authentication.getName());
        NotificationListResponse response = notificationQueryService.listLatest(account.getId(), limit, page, unreadOnly);
        return ResponseEntity.ok(ApiResponse.of(
                "notifications.list_success",
                messageResolver.get("notifications.list_success"),
                response
        ));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationMutationResponse>> markRead(Authentication authentication,
                                                                              @PathVariable String notificationId) {
        AccountMaster account = authService.getCurrentUserAccount(authentication.getName());
        NotificationMutationResponse response = notificationCommandService.markRead(account.getId(), notificationId);
        return ResponseEntity.ok(ApiResponse.of(
                "notifications.mark_read_success",
                messageResolver.get("notifications.mark_read_success"),
                response
        ));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<NotificationBulkMutationResponse>> markAllRead(Authentication authentication) {
        AccountMaster account = authService.getCurrentUserAccount(authentication.getName());
        NotificationBulkMutationResponse response = notificationCommandService.markAllRead(account.getId());
        return ResponseEntity.ok(ApiResponse.of(
                "notifications.mark_all_read_success",
                messageResolver.get("notifications.mark_all_read_success"),
                response
        ));
    }
}

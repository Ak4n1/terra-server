package com.terra.api.notifications.test;

import com.terra.api.common.i18n.message.MessageResolver;
import com.terra.api.common.response.ApiResponse;
import com.terra.api.notifications.dto.NotificationDispatchRequest;
import com.terra.api.notifications.dto.NotificationMutationResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/test/notifications")
public class NotificationTestController {

    private final NotificationTestService notificationTestService;
    private final MessageResolver messageResolver;

    public NotificationTestController(NotificationTestService notificationTestService,
                                      MessageResolver messageResolver) {
        this.notificationTestService = notificationTestService;
        this.messageResolver = messageResolver;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationMutationResponse>> dispatch(@RequestBody(required = false) NotificationDispatchRequest request) {
        NotificationMutationResponse response = notificationTestService.dispatch(
                request == null ? new NotificationDispatchRequest(null, null, null) : request
        );
        return ResponseEntity.ok(ApiResponse.of(
                "notifications.test_dispatch_success",
                messageResolver.get("notifications.test_dispatch_success"),
                response
        ));
    }
}

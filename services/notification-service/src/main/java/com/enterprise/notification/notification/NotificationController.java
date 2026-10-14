package com.enterprise.notification.notification;

import com.enterprise.notification.common.PageResponse;
import com.enterprise.notification.notification.NotificationDtos.CreateNotificationRequest;
import com.enterprise.notification.notification.NotificationDtos.NotificationResponse;
import com.enterprise.notification.notification.NotificationDtos.UnreadCount;
import com.enterprise.notification.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/api/notifications")
    @Operation(summary = "My notifications, newest first (admins also see admin alerts)")
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user, page, size);
    }

    @GetMapping("/api/notifications/unread-count")
    @Operation(summary = "Number of unread notifications (for the bell badge)")
    public UnreadCount unread(@AuthenticationPrincipal AuthenticatedUser user) {
        return new UnreadCount(service.unread(user));
    }

    @PatchMapping("/api/notifications/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public NotificationResponse markRead(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.markRead(id, user);
    }

    @PatchMapping("/api/notifications/read-all")
    @Operation(summary = "Mark all my notifications as read")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("updated", service.markAllRead(user));
    }

    @PostMapping("/internal/notifications")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a notification (service-to-service, not routed by the gateway)")
    public NotificationResponse create(@Valid @RequestBody CreateNotificationRequest request) {
        return service.create(request);
    }
}

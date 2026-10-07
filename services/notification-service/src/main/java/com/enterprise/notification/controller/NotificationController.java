package com.enterprise.notification.controller;

import com.enterprise.notification.entity.Notification;
import com.enterprise.notification.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody CreateNotificationRequest request) {
        return ResponseEntity.ok(toResponse(notificationService.sendNotification(request.userId(), request.type(), request.message())));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<NotificationResponse>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.getByUserId(userId).stream().map(this::toResponse).toList());
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getUserId(), notification.getType(), notification.getMessage(), notification.getStatus().name(), notification.getCreatedAt());
    }

    public record CreateNotificationRequest(@NotNull Long userId, @NotBlank @Size(max=255) String type, @NotBlank @Size(max=1000) String message) {}
    public record NotificationResponse(Long id, Long userId, String type, String message, String status, Instant createdAt) {}
}

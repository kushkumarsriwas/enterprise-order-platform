package com.enterprise.notification.notification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    /** Sent by the other services. When {@code email} is set the notification is also e-mailed. */
    public record CreateNotificationRequest(Long userId,
                                            @NotNull Audience audience,
                                            @NotBlank @Size(max = 40) String type,
                                            @NotBlank @Size(max = 150) String title,
                                            @NotBlank @Size(max = 1000) String message,
                                            @Email @Size(max = 120) String email) {
    }

    public record NotificationResponse(Long id, Long userId, String audience, String type, String title,
                                       String message, boolean read, Instant createdAt) {

        public static NotificationResponse from(Notification notification) {
            return new NotificationResponse(notification.getId(), notification.getUserId(),
                    notification.getAudience().name(), notification.getType(), notification.getTitle(),
                    notification.getMessage(), notification.isRead(), notification.getCreatedAt());
        }
    }

    public record UnreadCount(long unread) {
    }
}

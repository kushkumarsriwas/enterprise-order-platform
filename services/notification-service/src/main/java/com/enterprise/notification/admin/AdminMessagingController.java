package com.enterprise.notification.admin;

import com.enterprise.notification.common.PageResponse;
import com.enterprise.notification.email.EmailOutbox.EmailResponse;
import com.enterprise.notification.email.EmailOutbox;
import com.enterprise.notification.email.EmailStatus;
import com.enterprise.notification.messaging.MessagingDtos.AdminConversationResponse;
import com.enterprise.notification.messaging.MessagingDtos.AdminMessageResponse;
import com.enterprise.notification.messaging.MessagingService.MessagingStats;
import com.enterprise.notification.messaging.MessagingService;
import com.enterprise.notification.notification.NotificationDtos.NotificationResponse;
import com.enterprise.notification.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nexora Control Center - communications: every conversation between customers and sellers (read-only), reported
 * conversations, a search across all messages, every notification and every e-mail sent by the platform.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - messages, notifications and e-mails")
public class AdminMessagingController {

    private final MessagingService messaging;
    private final NotificationService notifications;
    private final EmailOutbox emails;

    public AdminMessagingController(MessagingService messaging, NotificationService notifications,
                                    EmailOutbox emails) {
        this.messaging = messaging;
        this.notifications = notifications;
        this.emails = emails;
    }

    @GetMapping("/conversations")
    @Operation(summary = "All customer-seller conversations (filters: customer, store, reported, text)")
    public PageResponse<AdminConversationResponse> conversations(@RequestParam(required = false) Long customerId,
                                                                 @RequestParam(required = false) Long sellerId,
                                                                 @RequestParam(required = false) Boolean reported,
                                                                 @RequestParam(required = false) String q,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "25") int size) {
        return messaging.adminSearch(customerId, sellerId, reported, q, page, size);
    }

    @GetMapping("/conversations/stats")
    @Operation(summary = "Conversations, reported conversations and message volume")
    public MessagingStats stats() {
        return messaging.stats();
    }

    @GetMapping("/conversations/{id}")
    public AdminConversationResponse conversation(@PathVariable Long id) {
        return messaging.adminGet(id);
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "The full thread, oldest first (read-only)")
    public PageResponse<AdminMessageResponse> messages(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "100") int size) {
        return messaging.adminMessages(id, page, size);
    }

    @PutMapping("/conversations/{id}/resolve-report")
    @Operation(summary = "Close the report of a conversation after review")
    public AdminConversationResponse resolve(@PathVariable Long id) {
        return messaging.resolveReport(id);
    }

    @GetMapping("/messages")
    @Operation(summary = "Search every message on the platform (text, product or order number)")
    public PageResponse<AdminMessageResponse> searchMessages(@RequestParam String q,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "25") int size) {
        return messaging.adminSearchMessages(q, page, size);
    }

    @GetMapping("/notifications")
    @Operation(summary = "Every notification sent to any user (filters: user, type, dates, text)")
    public PageResponse<NotificationResponse> allNotifications(@RequestParam(required = false) Long userId,
                                                               @RequestParam(required = false) String type,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                               @RequestParam(required = false) String q,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "25") int size) {
        return notifications.search(userId, type, from, to, q, page, size);
    }

    @GetMapping("/emails")
    @Operation(summary = "E-mail outbox: every e-mail sent by the platform and its delivery status")
    public PageResponse<EmailResponse> emails(@RequestParam(required = false) EmailStatus status,
                                              @RequestParam(required = false) Long userId,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "25") int size) {
        return emails.search(status, userId, q, page, size);
    }
}

package com.enterprise.notification.messaging;

import com.enterprise.notification.common.PageResponse;
import com.enterprise.notification.messaging.MessagingDtos.ConversationResponse;
import com.enterprise.notification.messaging.MessagingDtos.ConversationStarted;
import com.enterprise.notification.messaging.MessagingDtos.MessageResponse;
import com.enterprise.notification.messaging.MessagingDtos.SendMessageRequest;
import com.enterprise.notification.messaging.MessagingDtos.StartConversationRequest;
import com.enterprise.notification.messaging.MessagingDtos.UnreadMessages;
import com.enterprise.notification.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@Tag(name = "Messages between customers and sellers")
public class ConversationController {

    private final MessagingService messaging;

    public ConversationController(MessagingService messaging) {
        this.messaging = messaging;
    }

    @GetMapping
    @Operation(summary = "My conversations, most recent first")
    public PageResponse<ConversationResponse> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @AuthenticationPrincipal AuthenticatedUser user) {
        return messaging.list(user, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Contact a seller (customers); continues the existing thread with that store")
    public ConversationStarted start(@Valid @RequestBody StartConversationRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser user) {
        return messaging.start(user, request);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread messages across all my conversations (inbox badge)")
    public UnreadMessages unread(@AuthenticationPrincipal AuthenticatedUser user) {
        return new UnreadMessages(messaging.unread(user));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One conversation")
    public ConversationResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return messaging.get(user, id);
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Messages of a conversation, newest first")
    public PageResponse<MessageResponse> messages(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size,
                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return messaging.messages(user, id, page, size);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send a message (the customer or the seller of this conversation)")
    public MessageResponse send(@PathVariable Long id, @Valid @RequestBody SendMessageRequest request,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return messaging.send(user, id, request);
    }

    @PostMapping("/{id}/report")
    @Operation(summary = "Report a conversation (spam, abuse, scam) to Nexora support")
    public ConversationResponse report(@PathVariable Long id, @Valid @RequestBody MessagingDtos.ReportRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        return messaging.report(user, id, request.reason());
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark the conversation as read")
    public ConversationResponse markRead(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return messaging.markRead(user, id);
    }
}

package com.enterprise.notification.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class MessagingDtos {

    private MessagingDtos() {
    }

    /** "Contact seller" from a product or order page. */
    public record StartConversationRequest(@NotNull Long sellerId,
                                           @NotBlank @Size(max = 2000) String message,
                                           Long productId,
                                           @Size(max = 150) String productName,
                                           @Size(max = 40) String orderNumber) {
    }

    public record SendMessageRequest(@NotBlank @Size(max = 2000) String message,
                                     Long productId,
                                     @Size(max = 150) String productName,
                                     @Size(max = 40) String orderNumber) {
    }

    /** counterpartName: the store for a customer, the customer for a seller. unread: for the caller. */
    public record ConversationResponse(Long id, Long customerId, String customerName, Long sellerId,
                                       String sellerName, String counterpartName, String lastMessagePreview,
                                       Instant lastMessageAt, int unread, Instant createdAt) {

        static ConversationResponse of(Conversation c, Long viewerId) {
            return new ConversationResponse(c.getId(), c.getCustomerId(), c.getCustomerName(), c.getSellerId(),
                    c.getSellerName(), c.isCustomer(viewerId) ? c.getSellerName() : c.getCustomerName(),
                    c.getLastMessagePreview(), c.getLastMessageAt(), c.unreadFor(viewerId), c.getCreatedAt());
        }
    }

    public record MessageResponse(Long id, Long conversationId, Long senderId, String senderRole, String senderName,
                                  boolean mine, String body, Long productId, String productName, String orderNumber,
                                  Instant createdAt) {

        static MessageResponse of(Message m, Long conversationId, Long viewerId) {
            return new MessageResponse(m.getId(), conversationId, m.getSenderId(), m.getSenderRole(),
                    m.getSenderName(), m.getSenderId().equals(viewerId), m.getBody(), m.getProductId(),
                    m.getProductName(), m.getOrderNumber(), m.getCreatedAt());
        }
    }

    public record ConversationStarted(ConversationResponse conversation, MessageResponse message) {
    }

    public record ReportRequest(@NotBlank @Size(max = 255) String reason) {
    }

    /** The administrator's view: both parties, unread counters on both sides and the report status. */
    public record AdminConversationResponse(Long id, Long customerId, String customerName, Long sellerId,
                                            String sellerName, String lastMessagePreview, Instant lastMessageAt,
                                            int customerUnread, int sellerUnread, boolean reported,
                                            String reportReason, String reportedBy, Instant reportedAt,
                                            Instant createdAt) {

        public static AdminConversationResponse of(Conversation c) {
            return new AdminConversationResponse(c.getId(), c.getCustomerId(), c.getCustomerName(), c.getSellerId(),
                    c.getSellerName(), c.getLastMessagePreview(), c.getLastMessageAt(), c.getCustomerUnread(),
                    c.getSellerUnread(), c.isReported(), c.getReportReason(), c.getReportedBy(), c.getReportedAt(),
                    c.getCreatedAt());
        }
    }

    public record AdminMessageResponse(Long id, Long conversationId, String customerName, String sellerName,
                                       Long senderId, String senderRole, String senderName, String body,
                                       Long productId, String productName, String orderNumber, Instant createdAt) {

        public static AdminMessageResponse of(Message m) {
            Conversation c = m.getConversation();
            return new AdminMessageResponse(m.getId(), c.getId(), c.getCustomerName(), c.getSellerName(),
                    m.getSenderId(), m.getSenderRole(), m.getSenderName(), m.getBody(), m.getProductId(),
                    m.getProductName(), m.getOrderNumber(), m.getCreatedAt());
        }
    }

    public record UnreadMessages(long unread) {
    }
}

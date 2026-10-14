package com.enterprise.notification.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class MessagingDtos {

    private MessagingDtos() {
    }

    /** "Contact seller" from a product or order page. */
    public record StartConversationRequest(@NotNull Long merchantId,
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
    public record ConversationResponse(Long id, Long customerId, String customerName, Long merchantId,
                                       String merchantName, String counterpartName, String lastMessagePreview,
                                       Instant lastMessageAt, int unread, Instant createdAt) {

        static ConversationResponse of(Conversation c, Long viewerId) {
            return new ConversationResponse(c.getId(), c.getCustomerId(), c.getCustomerName(), c.getMerchantId(),
                    c.getMerchantName(), c.isCustomer(viewerId) ? c.getMerchantName() : c.getCustomerName(),
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

    public record UnreadMessages(long unread) {
    }
}

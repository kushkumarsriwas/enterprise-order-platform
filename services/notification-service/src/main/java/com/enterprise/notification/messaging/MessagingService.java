package com.enterprise.notification.messaging;

import com.enterprise.notification.client.StoreDirectoryClient;
import com.enterprise.notification.common.ApiException;
import com.enterprise.notification.common.Like;
import com.enterprise.notification.common.PageResponse;
import com.enterprise.notification.messaging.MessagingDtos.AdminConversationResponse;
import com.enterprise.notification.messaging.MessagingDtos.AdminMessageResponse;
import com.enterprise.notification.messaging.MessagingDtos.ConversationResponse;
import com.enterprise.notification.messaging.MessagingDtos.ConversationStarted;
import com.enterprise.notification.messaging.MessagingDtos.MessageResponse;
import com.enterprise.notification.messaging.MessagingDtos.SendMessageRequest;
import com.enterprise.notification.messaging.MessagingDtos.StartConversationRequest;
import com.enterprise.notification.notification.Audience;
import com.enterprise.notification.notification.Notification;
import com.enterprise.notification.notification.NotificationRepository;
import com.enterprise.notification.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Customer-seller messaging ("Contact seller"), with an in-app notification for new messages. */
@Service
public class MessagingService {

    private static final Logger log = LoggerFactory.getLogger(MessagingService.class);

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final NotificationRepository notifications;
    private final StoreDirectoryClient stores;
    private final TransactionTemplate tx;

    public MessagingService(ConversationRepository conversations, MessageRepository messages,
                            NotificationRepository notifications, StoreDirectoryClient stores,
                            TransactionTemplate tx) {
        this.conversations = conversations;
        this.messages = messages;
        this.notifications = notifications;
        this.stores = stores;
        this.tx = tx;
    }

    /**
     * Opens (or continues) the customer's thread with a store. The store is checked with the account service
     * before the database transaction starts, so no connection is held during the remote call.
     */
    public ConversationStarted start(AuthenticatedUser customer, StartConversationRequest request) {
        if (!customer.isCustomer()) {
            throw ApiException.forbidden("Only customers can start a conversation with a seller");
        }
        String storeName = conversations.findByCustomerIdAndSellerId(customer.id(), request.sellerId())
                .map(Conversation::getSellerName)
                .orElseGet(() -> stores.storeName(request.sellerId()));
        return tx.execute(status -> {
            Conversation conversation = conversations.findForUpdate(customer.id(), request.sellerId())
                    .orElseGet(() -> conversations.saveAndFlush(new Conversation(customer.id(), customer.username(),
                            request.sellerId(), storeName)));
            Message message = append(conversation, customer, request.message(), request.productId(),
                    request.productName(), request.orderNumber());
            return new ConversationStarted(ConversationResponse.of(conversation, customer.id()),
                    MessageResponse.of(message, conversation.getId(), customer.id()));
        });
    }

    @Transactional
    public MessageResponse send(AuthenticatedUser sender, Long conversationId, SendMessageRequest request) {
        Conversation conversation = conversations.findForUpdate(conversationId)
                .filter(c -> c.hasParticipant(sender.id()))
                .orElseThrow(() -> notFound(conversationId));
        Message message = append(conversation, sender, request.message(), request.productId(), request.productName(),
                request.orderNumber());
        return MessageResponse.of(message, conversationId, sender.id());
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> list(AuthenticatedUser user, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "lastMessageAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Conversation> result = user.isSeller() ? conversations.findBySellerId(user.id(), pageable)
                : conversations.findByCustomerId(user.id(), pageable);
        return PageResponse.from(result, c -> ConversationResponse.of(c, user.id()));
    }

    @Transactional(readOnly = true)
    public ConversationResponse get(AuthenticatedUser user, Long id) {
        return ConversationResponse.of(find(user, id), user.id());
    }

    /** Newest first; the chat window shows them bottom-up. */
    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> messages(AuthenticatedUser user, Long id, int page, int size) {
        Conversation conversation = find(user, id);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(messages.findByConversationId(conversation.getId(), pageable),
                m -> MessageResponse.of(m, conversation.getId(), user.id()));
    }

    @Transactional
    public ConversationResponse markRead(AuthenticatedUser user, Long id) {
        Conversation conversation = conversations.findForUpdate(id)
                .filter(c -> c.hasParticipant(user.id()))
                .orElseThrow(() -> notFound(id));
        conversation.markReadBy(user.id());
        return ConversationResponse.of(conversation, user.id());
    }

    /** A participant reports the conversation; the administrator is alerted and reviews it. */
    @Transactional
    public ConversationResponse report(AuthenticatedUser user, Long id, String reason) {
        Conversation conversation = conversations.findForUpdate(id)
                .filter(c -> c.hasParticipant(user.id()))
                .orElseThrow(() -> notFound(id));
        conversation.report(user.displayName(), reason.trim());
        notifications.save(new Notification(null, Audience.ADMINS, "CONVERSATION_REPORTED",
                truncate("Conversation reported: " + conversation.getCustomerName() + " / "
                        + conversation.getSellerName(), 150), truncate(reason.trim(), 1000)));
        log.info("Conversation {} reported by {}", id, user.username());
        return ConversationResponse.of(conversation, user.id());
    }

    // ------------------------------------------------------------------ administrator (read-only oversight)

    @Transactional(readOnly = true)
    public PageResponse<AdminConversationResponse> adminSearch(Long customerId, Long sellerId, Boolean reported,
                                                               String query, int page, int size) {
        Specification<Conversation> spec = (root, criteria, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (customerId != null) {
                where.add(cb.equal(root.get("customerId"), customerId));
            }
            if (sellerId != null) {
                where.add(cb.equal(root.get("sellerId"), sellerId));
            }
            if (reported != null) {
                where.add(cb.equal(root.get("reported"), reported));
            }
            if (query != null && !query.isBlank()) {
                String pattern = Like.contains(query);
                where.add(cb.or(cb.like(cb.lower(root.get("customerName")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(root.get("sellerName")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("lastMessagePreview"), "")), pattern,
                                Like.ESCAPE)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "lastMessageAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(conversations.findAll(spec, pageable), AdminConversationResponse::of);
    }

    @Transactional(readOnly = true)
    public AdminConversationResponse adminGet(Long id) {
        return conversations.findById(id).map(AdminConversationResponse::of).orElseThrow(() -> notFound(id));
    }

    /** The whole thread, oldest first, as the administrator reads it. */
    @Transactional(readOnly = true)
    public PageResponse<AdminMessageResponse> adminMessages(Long id, int page, int size) {
        conversations.findById(id).orElseThrow(() -> notFound(id));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        return PageResponse.from(messages.findByConversationId(id, pageable), AdminMessageResponse::of);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminMessageResponse> adminSearchMessages(String query, int page, int size) {
        if (query == null || query.isBlank()) {
            throw ApiException.badRequest("Enter a word to search for");
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(messages.search(Like.contains(query), pageable), AdminMessageResponse::of);
    }

    @Transactional
    public AdminConversationResponse resolveReport(Long id) {
        Conversation conversation = conversations.findForUpdate(id).orElseThrow(() -> notFound(id));
        conversation.resolveReport();
        return AdminConversationResponse.of(conversation);
    }

    @Transactional(readOnly = true)
    public MessagingStats stats() {
        return new MessagingStats(conversations.count(), conversations.countByReportedTrue(), messages.count(),
                messages.countByCreatedAtAfter(java.time.Instant.now().minus(java.time.Duration.ofDays(1))));
    }

    public record MessagingStats(long conversations, long reportedConversations, long messages,
                                 long messagesLast24Hours) {
    }

    @Transactional(readOnly = true)
    public long unread(AuthenticatedUser user) {
        Long count = user.isSeller() ? conversations.unreadForSeller(user.id())
                : conversations.unreadForCustomer(user.id());
        return count == null ? 0 : count;
    }

    private Message append(Conversation conversation, AuthenticatedUser sender, String text, Long productId,
                           String productName, String orderNumber) {
        boolean fromCustomer = conversation.isCustomer(sender.id());
        String body = text.trim();
        Message message = messages.save(new Message(conversation, sender.id(), fromCustomer ? "CUSTOMER" : "SELLER",
                fromCustomer ? conversation.getCustomerName() : conversation.getSellerName(), body, productId,
                blankToNull(productName), blankToNull(orderNumber)));
        int unreadBefore = fromCustomer ? conversation.getSellerUnread() : conversation.getCustomerUnread();
        conversation.recordMessage(preview(body), fromCustomer);
        // one notification per burst of messages, not one per message
        if (unreadBefore == 0) {
            Long recipient = fromCustomer ? conversation.getSellerId() : conversation.getCustomerId();
            String from = fromCustomer ? conversation.getCustomerName() : conversation.getSellerName();
            String about = message.getProductName() != null ? " about " + message.getProductName()
                    : message.getOrderNumber() != null ? " about order " + message.getOrderNumber() : "";
            notifications.save(new Notification(recipient, Audience.USER, "NEW_MESSAGE",
                    truncate("New message from " + from + about, 150), preview(body)));
        }
        log.info("Message {} in conversation {} from {} {}", message.getId(), conversation.getId(),
                message.getSenderRole(), sender.id());
        return message;
    }

    private Conversation find(AuthenticatedUser user, Long id) {
        return conversations.findById(id).filter(c -> c.hasParticipant(user.id())).orElseThrow(() -> notFound(id));
    }

    private static ApiException notFound(Long id) {
        return ApiException.notFound("Conversation " + id + " not found");
    }

    private static String preview(String body) {
        return truncate(body.replaceAll("\\s+", " "), 200);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.enterprise.notification.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** One message. Can mention the product or order it is about, shown as a chip in the chat. */
@Entity
@Table(name = "messages", indexes = @Index(name = "idx_messages_conversation_created",
        columnList = "conversation_id, created_at"))
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /** CUSTOMER or SELLER. */
    @Column(name = "sender_role", nullable = false, length = 10)
    private String senderRole;

    @Column(name = "sender_name", nullable = false, length = 100)
    private String senderName;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", length = 150)
    private String productName;

    @Column(name = "order_number", length = 40)
    private String orderNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Message() {
    }

    public Message(Conversation conversation, Long senderId, String senderRole, String senderName, String body,
                   Long productId, String productName, String orderNumber) {
        this.conversation = conversation;
        this.senderId = senderId;
        this.senderRole = senderRole;
        this.senderName = senderName;
        this.body = body;
        this.productId = productId;
        this.productName = productName;
        this.orderNumber = orderNumber;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public Long getSenderId() {
        return senderId;
    }

    public String getSenderRole() {
        return senderRole;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getBody() {
        return body;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

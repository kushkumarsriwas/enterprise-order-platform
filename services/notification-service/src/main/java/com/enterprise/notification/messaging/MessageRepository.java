package com.enterprise.notification.messaging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationId(Long conversationId, Pageable pageable);

    /** Full-text-like search over every message on the platform (administrator). */
    @org.springframework.data.jpa.repository.Query(value = """
            select m from Message m join fetch m.conversation c
            where lower(m.body) like :pattern escape '\\'
               or lower(coalesce(m.productName, '')) like :pattern escape '\\'
               or lower(coalesce(m.orderNumber, '')) like :pattern escape '\\'""",
            countQuery = """
            select count(m) from Message m
            where lower(m.body) like :pattern escape '\\'
               or lower(coalesce(m.productName, '')) like :pattern escape '\\'
               or lower(coalesce(m.orderNumber, '')) like :pattern escape '\\'""")
    Page<Message> search(String pattern, Pageable pageable);

    long countByCreatedAtAfter(java.time.Instant since);
}

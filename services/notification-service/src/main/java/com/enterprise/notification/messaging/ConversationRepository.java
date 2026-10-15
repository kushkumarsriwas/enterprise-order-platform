package com.enterprise.notification.messaging;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ConversationRepository extends JpaRepository<Conversation, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Conversation> {

    Optional<Conversation> findByCustomerIdAndSellerId(Long customerId, Long sellerId);

    Page<Conversation> findByCustomerId(Long customerId, Pageable pageable);

    Page<Conversation> findBySellerId(Long sellerId, Pageable pageable);

    /**
     * SELECT ... FOR UPDATE: messages sent at the same moment by both sides are applied one after the other,
     * so the unread counters are never lost (pessimistic lock - a chat should not fail on a version conflict).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.customerId = :customerId and c.sellerId = :sellerId")
    Optional<Conversation> findForUpdate(Long customerId, Long sellerId);

    long countByReportedTrue();

    @Query("select sum(c.customerUnread) from Conversation c where c.customerId = :userId")
    Long unreadForCustomer(Long userId);

    @Query("select sum(c.sellerUnread) from Conversation c where c.sellerId = :userId")
    Long unreadForSeller(Long userId);
}

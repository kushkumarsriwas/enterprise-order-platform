package com.enterprise.notification.email;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long>, JpaSpecificationExecutor<EmailMessage> {

    List<EmailMessage> findByStatusInAndAttemptsLessThanAndCreatedAtBeforeOrderByCreatedAt(
            Collection<EmailStatus> statuses, int maxAttempts, Instant before, Limit limit);
}

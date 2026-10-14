package com.enterprise.notification.email;

import com.enterprise.notification.common.Like;
import com.enterprise.notification.common.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/** Outbox pattern for e-mails: queue in the business transaction, send after commit, retry failures. */
@Service
public class EmailOutbox {

    private static final Logger log = LoggerFactory.getLogger(EmailOutbox.class);
    private static final int MAX_ATTEMPTS = 5;

    private final EmailMessageRepository repository;
    private final EmailSender sender;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    public EmailOutbox(EmailMessageRepository repository, EmailSender sender, ApplicationEventPublisher events,
                       PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.sender = sender;
        this.events = events;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Must run inside the caller's transaction (the e-mail commits or rolls back with the notification). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void queue(String to, Long userId, String type, String subject, String body) {
        EmailMessage email = repository.save(new EmailMessage(to, userId, type, subject, body));
        events.publishEvent(new EmailQueued(email.getId()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(EmailQueued event) {
        deliver(event.emailId());
    }

    /** Safety net: e-mails that failed (or were queued while the app stopped) are retried every minute. */
    @Scheduled(fixedDelayString = "${app.mail.retry-interval:60s}", initialDelayString = "60s")
    public void retryFailed() {
        List<EmailMessage> pending = repository.findByStatusInAndAttemptsLessThanAndCreatedAtBeforeOrderByCreatedAt(
                List.of(EmailStatus.QUEUED, EmailStatus.FAILED), MAX_ATTEMPTS, Instant.now().minusSeconds(30),
                Limit.of(50));
        pending.forEach(email -> deliver(email.getId()));
    }

    /** Sends one e-mail in its own transaction and records the result (SENT, or FAILED with the error). */
    public void deliver(Long emailId) {
        tx.executeWithoutResult(status -> repository.findById(emailId)
                .filter(email -> email.getStatus() != EmailStatus.SENT)
                .ifPresent(email -> {
                    try {
                        sender.send(email.getToAddress(), email.getSubject(), email.getBody());
                        email.sent();
                    } catch (RuntimeException ex) {
                        log.warn("E-mail {} could not be sent: {}", emailId, ex.getMessage());
                        email.failed(ex.getMessage());
                    }
                }));
    }

    @Transactional(readOnly = true)
    public PageResponse<EmailResponse> search(EmailStatus status, Long userId, String query, int page, int size) {
        Specification<EmailMessage> spec = (root, criteria, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            if (userId != null) {
                where.add(cb.equal(root.get("userId"), userId));
            }
            if (query != null && !query.isBlank()) {
                String pattern = Like.contains(query);
                where.add(cb.or(cb.like(cb.lower(root.get("toAddress")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(root.get("subject")), pattern, Like.ESCAPE)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(repository.findAll(spec, pageable), EmailResponse::from);
    }

    public record EmailQueued(Long emailId) {
    }

    public record EmailResponse(Long id, String toAddress, Long userId, String notificationType, String subject,
                                String body, String status, int attempts, String lastError, Instant createdAt,
                                Instant sentAt) {

        static EmailResponse from(EmailMessage e) {
            return new EmailResponse(e.getId(), e.getToAddress(), e.getUserId(), e.getNotificationType(),
                    e.getSubject(), e.getBody(), e.getStatus().name(), e.getAttempts(), e.getLastError(),
                    e.getCreatedAt(), e.getSentAt());
        }
    }
}

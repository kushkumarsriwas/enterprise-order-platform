package com.enterprise.notification.notification;

import com.enterprise.notification.common.ApiException;
import com.enterprise.notification.common.Like;
import com.enterprise.notification.common.PageResponse;
import com.enterprise.notification.email.EmailOutbox;
import com.enterprise.notification.notification.NotificationDtos.CreateNotificationRequest;
import com.enterprise.notification.notification.NotificationDtos.NotificationResponse;
import com.enterprise.notification.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** In-app notifications (the bell) and, when an address is given, the matching e-mail through the outbox. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final EmailOutbox emailOutbox;

    public NotificationService(NotificationRepository repository, EmailOutbox emailOutbox) {
        this.repository = repository;
        this.emailOutbox = emailOutbox;
    }

    @Transactional
    public NotificationResponse create(CreateNotificationRequest request) {
        if (request.audience() == Audience.USER && request.userId() == null) {
            throw ApiException.badRequest("userId is required for USER notifications");
        }
        Long userId = request.audience() == Audience.USER ? request.userId() : null;
        Notification saved = repository.save(new Notification(userId, request.audience(), request.type(),
                request.title(), request.message()));
        log.info("Notification {} [{}] created for {}", saved.getId(), request.type(),
                userId == null ? "admins" : "user " + userId);
        if (request.email() != null && !request.email().isBlank()) {
            emailOutbox.queue(request.email().trim(), userId, request.type(), request.title(), request.message());
        }
        return NotificationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(AuthenticatedUser user, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(repository.findVisibleTo(user.id(), user.isAdmin(), pageable),
                NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unread(AuthenticatedUser user) {
        return repository.countUnread(user.id(), user.isAdmin());
    }

    @Transactional
    public NotificationResponse markRead(Long id, AuthenticatedUser user) {
        Notification notification = repository.findById(id)
                .filter(n -> n.getAudience() == Audience.ADMINS ? user.isAdmin() : user.id().equals(n.getUserId()))
                .orElseThrow(() -> ApiException.notFound("Notification " + id + " not found"));
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(AuthenticatedUser user) {
        return repository.markAllRead(user.id(), user.isAdmin());
    }

    /** The administrator's view of every notification sent on the platform. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> search(Long userId, String type, LocalDate from, LocalDate to,
                                                     String query, int page, int size) {
        Specification<Notification> spec = (root, criteria, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (userId != null) {
                where.add(cb.equal(root.get("userId"), userId));
            }
            if (type != null && !type.isBlank()) {
                where.add(cb.equal(root.get("type"), type.trim().toUpperCase()));
            }
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"),
                        from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (to != null) {
                where.add(cb.lessThan(root.<Instant>get("createdAt"),
                        to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (query != null && !query.isBlank()) {
                String pattern = Like.contains(query);
                where.add(cb.or(cb.like(cb.lower(root.get("title")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(root.get("message")), pattern, Like.ESCAPE)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(repository.findAll(spec, pageable), NotificationResponse::from);
    }
}

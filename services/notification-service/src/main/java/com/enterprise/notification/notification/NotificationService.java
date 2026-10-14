package com.enterprise.notification.notification;

import com.enterprise.notification.common.ApiException;
import com.enterprise.notification.common.PageResponse;
import com.enterprise.notification.notification.NotificationDtos.CreateNotificationRequest;
import com.enterprise.notification.notification.NotificationDtos.NotificationResponse;
import com.enterprise.notification.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
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
}
